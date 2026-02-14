package core

import (
	"context"
	"crypto/tls"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/miekg/dns"
)

type dnsBackend interface {
	Exchange(m *dns.Msg) (*dns.Msg, string, error)
}

type cacheEntry struct {
	ip        string
	expiresAt time.Time
}

type Resolver struct {
	config  *Config
	backend dnsBackend
	cache   map[string]cacheEntry
	cacheMu sync.RWMutex
	cb      EngineCallbacks
}

func NewResolver(cfg *Config, cb EngineCallbacks) *Resolver {
	r := &Resolver{
		config: cfg,
		cache:  make(map[string]cacheEntry),
		cb:     cb,
	}
	r.backend = newBackend(cfg)
	go r.cleanCacheRoutine()
	return r
}

func (r *Resolver) Resolve(ctx context.Context, host string) (string, error) {
	if net.ParseIP(host) != nil {
		return host, nil
	}

	LogDebug("Resolving host: %s", host)

	globalEngine.mu.RLock()
	rules := globalEngine.rules
	globalEngine.mu.RUnlock()
	if ip, ok := rules.GetHost(host); ok && net.ParseIP(ip) != nil {
		LogDebug("DNS Rule Match: %s -> %s", host, ip)
		return ip, nil
	}

	if ip, ok := r.getCache(host, dns.TypeA); ok {
		LogDebug("DNS Cache Hit: %s -> %s", host, ip)
		return ip, nil
	}

	if r.backend == nil {
		LogDebug("No DNS backend, using system resolver for %s", host)
		return r.resolveSystem(ctx, host)
	}

	ip, err := r.resolveRemote(ctx, host)
	if err == nil {
		return ip, nil
	}

	LogWarn("Remote DNS failed for %s, falling back to system: %v", host, err)
	return r.resolveSystem(ctx, host)
}

func (r *Resolver) resolveRemote(ctx context.Context, host string) (string, error) {
	m := new(dns.Msg)
	m.SetQuestion(dns.Fqdn(host), dns.TypeA)
	m.RecursionDesired = true

	reply, addr, err := r.backend.Exchange(m)
	if err != nil {
		return "", err
	}

	if reply.Rcode != dns.RcodeSuccess {
		return "", fmt.Errorf("dns error: %s", dns.RcodeToString[reply.Rcode])
	}

	for _, ans := range reply.Answer {
		if a, ok := ans.(*dns.A); ok {
			ip := a.A.String()
			r.setCache(host, ip, dns.TypeA, a.Hdr.Ttl)
			LogInfo("DNS: %s -> %s (%s)", host, ip, addr)
			return ip, nil
		}
	}

	return "", fmt.Errorf("no A record")
}

func (r *Resolver) resolveSystem(ctx context.Context, host string) (string, error) {
	ips, err := net.DefaultResolver.LookupHost(ctx, host)
	if err != nil {
		return "", err
	}
	return ips[0], nil
}

func (r *Resolver) getCache(host string, qType uint16) (string, bool) {
	r.cacheMu.RLock()
	defer r.cacheMu.RUnlock()
	key := fmt.Sprintf("%s:%d", host, qType)
	if entry, ok := r.cache[key]; ok && time.Now().Before(entry.expiresAt) {
		return entry.ip, true
	}
	return "", false
}

func (r *Resolver) setCache(host, ip string, qType uint16, ttl uint32) {
	if ttl == 0 {
		ttl = 300
	}
	if ttl > 86400 {
		ttl = 86400
	}
	r.cacheMu.Lock()
	defer r.cacheMu.Unlock()
	key := fmt.Sprintf("%s:%d", host, qType)
	r.cache[key] = cacheEntry{
		ip:        ip,
		expiresAt: time.Now().Add(time.Duration(ttl) * time.Second),
	}
}

func (r *Resolver) cleanCacheRoutine() {
	ticker := time.NewTicker(10 * time.Minute)
	for range ticker.C {
		r.cacheMu.Lock()
		now := time.Now()
		for k, v := range r.cache {
			if now.After(v.expiresAt) {
				delete(r.cache, k)
			}
		}
		r.cacheMu.Unlock()
	}
}

type stdBackend struct {
	upstreams []stdUpstream
	timeout   time.Duration
}

type stdUpstream interface {
	Exchange(m *dns.Msg) (*dns.Msg, error)
	Address() string
}

func (b *stdBackend) Exchange(m *dns.Msg) (*dns.Msg, string, error) {
	type result struct {
		reply *dns.Msg
		addr  string
		err   error
	}
	resCh := make(chan result, len(b.upstreams))

	for _, u := range b.upstreams {
		go func(u stdUpstream) {
			reply, err := u.Exchange(m)
			resCh <- result{reply, u.Address(), err}
		}(u)
	}

	var lastErr error
	for i := 0; i < len(b.upstreams); i++ {
		select {
		case res := <-resCh:
			if res.err == nil && res.reply != nil {
				return res.reply, res.addr, nil
			}
			lastErr = res.err
		case <-time.After(b.timeout):
			if lastErr != nil {
				return nil, "", lastErr
			}
			return nil, "", fmt.Errorf("dns timeout")
		}
	}
	return nil, "", lastErr
}

func newBackend(cfg *Config) dnsBackend {
	timeout := 5 * time.Second
	var upstreams []stdUpstream

	for _, bDns := range cfg.BootstrapDNS {
		if bDns != "" {
			u, err := parseUpstream(bDns, timeout)
			if err == nil {
				upstreams = append(upstreams, u)
			}
		}
	}
	if len(upstreams) == 0 {
		u, _ := parseUpstream("223.5.5.5:53", timeout)
		upstreams = append(upstreams, u)
	}

	for _, ns := range cfg.NameServers {
		u, err := parseUpstream(ns, timeout)
		if err == nil {
			upstreams = append(upstreams, u)
		}
	}
	return &stdBackend{upstreams: upstreams, timeout: timeout}
}

func parseUpstream(addr string, timeout time.Duration) (stdUpstream, error) {
	if strings.HasPrefix(addr, "https://") {
		return &dohUpstream{addr: addr, client: &http.Client{Timeout: timeout}}, nil
	}
	if strings.HasPrefix(addr, "tls://") {
		host := strings.TrimPrefix(addr, "tls://")
		if !strings.Contains(host, ":") {
			host += ":853"
		}
		return &dnsUpstream{addr: host, network: "tcp-tls", timeout: timeout}, nil
	}
	// Default to UDP
	host := addr
	if !strings.Contains(host, ":") {
		host += ":53"
	}
	return &dnsUpstream{addr: host, network: "udp", timeout: timeout}, nil
}

type dnsUpstream struct {
	addr    string
	network string
	timeout time.Duration
}

func (u *dnsUpstream) Address() string { return u.addr }
func (u *dnsUpstream) Exchange(m *dns.Msg) (*dns.Msg, error) {
	client := &dns.Client{
		Net:     u.network,
		Timeout: u.timeout,
		Dialer:  getProtectedDialer(),
	}
	if u.network == "tcp-tls" {
		client.TLSConfig = &tls.Config{InsecureSkipVerify: false}
	}
	reply, _, err := client.Exchange(m, u.addr)
	return reply, err
}

type dohUpstream struct {
	addr   string
	client *http.Client
}

func (u *dohUpstream) Address() string { return u.addr }
func (u *dohUpstream) Exchange(m *dns.Msg) (*dns.Msg, error) {
	if u.client.Transport == nil {
		u.client.Transport = &http.Transport{
			DialContext: getProtectedDialer().DialContext,
		}
	}
	data, err := m.Pack()
	if err != nil {
		return nil, err
	}
	req, err := http.NewRequest("POST", u.addr, strings.NewReader(string(data)))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/dns-message")
	req.Header.Set("Accept", "application/dns-message")

	resp, err := u.client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("doh error: %d", resp.StatusCode)
	}

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}

	reply := new(dns.Msg)
	err = reply.Unpack(body)
	return reply, err
}

func handleDNSConnection(conn net.Conn, cb EngineCallbacks) {
	defer func() {
		if r := recover(); r != nil {
			log.Printf("PANIC in handleDNSConnection: %v", r)
		}
		conn.Close()
	}()

	buf := make([]byte, 2048)
	n, err := conn.Read(buf)
	if err != nil {
		LogDebug("DNS Connection Read Error: %v", err)
		return
	}

	msg := new(dns.Msg)
	if err := msg.Unpack(buf[:n]); err != nil {
		LogWarn("DNS Unpack Error: %v", err)
		return
	}

	if len(msg.Question) > 0 {
		qName := strings.TrimSuffix(msg.Question[0].Name, ".")
		qType := msg.Question[0].Qtype
		LogDebug("Inbound DNS Query: %s (Type: %d)", qName, qType)

		globalEngine.mu.RLock()
		cfg := globalEngine.config
		rules := globalEngine.rules
		globalEngine.mu.RUnlock()

		if cfg != nil && !cfg.EnableIPv6 && qType == dns.TypeAAAA {
			LogInfo("DNS: Blocking AAAA query for %s (IPv6 disabled)", qName)
			reply := new(dns.Msg)
			reply.SetReply(msg)
			replyData, _ := reply.Pack()
			conn.Write(replyData)
			return
		}

		if ip, ok := rules.GetHost(qName); ok && net.ParseIP(ip) != nil {
			if qType == dns.TypeA {
				LogInfo("DNS Hijack: %s -> %s (Rule Match)", qName, ip)
				reply := new(dns.Msg)
				reply.SetReply(msg)

				targetIP := ip
				rr, err := dns.NewRR(fmt.Sprintf("%s 3600 IN A %s", msg.Question[0].Name, targetIP))
				if err == nil {
					reply.Answer = append(reply.Answer, rr)
					replyData, _ := reply.Pack()
					conn.Write(replyData)
					return
				}
			} else if qType == dns.TypeAAAA {
				// Return empty success for AAAA if we hijack A, to avoid IPv6 issues
				LogInfo("DNS Hijack (AAAA): %s -> EMPTY (Rule Match)", qName)
				reply := new(dns.Msg)
				reply.SetReply(msg)
				replyData, _ := reply.Pack()
				conn.Write(replyData)
				return
			}
		}
	}

	globalEngine.mu.RLock()
	resolver := globalEngine.resolver
	globalEngine.mu.RUnlock()

	if resolver == nil || resolver.backend == nil {
		LogWarn("DNS Resolver or backend not initialized")
		return
	}

	reply, _, err := resolver.backend.Exchange(msg)
	if err != nil {
		LogError("DNS Exchange Error: %v", err)
		return
	}

	replyData, err := reply.Pack()
	if err == nil {
		conn.Write(replyData)
	} else {
		LogError("DNS Pack Error: %v", err)
	}
}
