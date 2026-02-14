package core

import (
	"context"
	"crypto/tls"
	"encoding/json"
	"encoding/pem"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"net/url"
	"sync/atomic"
	"syscall"
	"time"

	ruleslib "github.com/xihale/snirect-shared/rules"
)

type EngineCallbacks interface {
	OnStatusChanged(status string)
	OnSpeedUpdated(up int64, down int64)
	Protect(fd int) bool
}

func getProtectedDialer() *net.Dialer {
	return &net.Dialer{
		Timeout: 10 * time.Second,
		Control: func(network, address string, c syscall.RawConn) error {
			globalEngine.mu.RLock()
			cb := globalEngine.cb
			globalEngine.mu.RUnlock()

			if cb == nil {
				return nil
			}
			var err error
			controlErr := c.Control(func(fd uintptr) {
				if !cb.Protect(int(fd)) {
					err = fmt.Errorf("failed to protect socket fd %d", fd)
				} else {
					log.Printf("VPN: Protected socket fd %d", fd)
				}
			})
			if controlErr != nil {
				return controlErr
			}
			return err
		},
	}
}

var (
	certManager *CertManager
	activeStack *TunStack
	dataDir     string

	uploadBytes   int64
	downloadBytes int64
	lastUp        int64
	lastDown      int64
	speedTicker   *time.Ticker
	speedStop     chan struct{}
)

func SetDataDir(path string) {
	dataDir = path
}

func getCAPaths() (string, string) {
	if dataDir == "" {
		// Fallback for development/testing
		return "ca.crt", "ca.key"
	}
	return dataDir + "/ca.crt", dataDir + "/ca.key"
}

func StartEngine(fd int, configStr string, cb EngineCallbacks) {
	cbMutex.Lock()
	lastCb = cb
	cbMutex.Unlock()

	uploadBytes = 0
	downloadBytes = 0
	lastUp = 0
	lastDown = 0
	speedStop = make(chan struct{})
	speedTicker = time.NewTicker(1 * time.Second)
	go func() {
		for {
			select {
			case <-speedTicker.C:
				up := atomic.LoadInt64(&uploadBytes)
				down := atomic.LoadInt64(&downloadBytes)
				cb.OnSpeedUpdated(up-lastUp, down-lastDown)
				lastUp = up
				lastDown = down
			case <-speedStop:
				return
			}
		}
	}()

	var tempConfig struct {
		LogLevel string `json:"log_level"`
	}
	if err := json.Unmarshal([]byte(configStr), &tempConfig); err == nil {
		SetLogLevel(tempConfig.LogLevel)
	}

	LogInfo("CORE: Starting... FD=%d", fd)

	caPath, keyPath := getCAPaths()
	var err error
	certManager, err = NewCertManager(caPath, keyPath)
	if err != nil {
		LogError("CORE: CA Init Error: %v", err)
	} else {
		LogInfo("CORE: CA ready")
	}

	config, err := InitEngine(configStr, cb)
	if err != nil {
		LogError("CORE: Engine Init Error: %v", err)
	}

	ts, err := NewTunStack(fd, config, cb)
	if err != nil {
		LogError("CORE: TUN Setup Failed: %v", err)
		return
	}
	activeStack = ts
	ts.Start()
}

func StopEngine() {
	if speedTicker != nil {
		speedTicker.Stop()
		close(speedStop)
		speedTicker = nil
	}

	if activeStack != nil {
		activeStack.Stop()
		activeStack = nil
	}

	if certManager != nil {
		certManager.Close()
		certManager = nil
	}

	cbMutex.Lock()
	lastCb = nil
	cbMutex.Unlock()
}

func GetCACertificate() []byte {
	LogDebug("CORE: GetCACertificate called")

	caPath, keyPath := getCAPaths()

	if certManager == nil || certManager.RootCert == nil {
		LogInfo("CORE: Loading CA from %s", caPath)
		cm, err := NewCertManager(caPath, keyPath)
		if err != nil {
			LogError("CORE: CA Load Failed: %v", err)
			return nil
		}
		certManager = cm
	}

	if certManager != nil && certManager.RootCert != nil {
		LogDebug("CORE: Returning PEM cert")
		return pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: certManager.RootCert.Raw})
	}

	return nil
}

func UpdateRules(configStr string) error {
	var config Config
	if err := json.Unmarshal([]byte(configStr), &config); err != nil {
		return err
	}

	// Start with existing base rules (fetched rules)
	globalEngine.mu.RLock()
	baseRules := globalEngine.rules
	if baseRules == nil {
		baseRules = ruleslib.NewRules()
	}
	globalEngine.mu.RUnlock()

	// Build user rules from config
	userAlterHostname := make(map[string]string)
	for _, rule := range config.Rules {
		for _, pattern := range rule.Patterns {
			if rule.TargetSNI != nil {
				userAlterHostname[pattern] = *rule.TargetSNI
			}
			if rule.TargetIP != nil {
				userAlterHostname[pattern+"|hosts"] = *rule.TargetIP
			}
		}
	}

	userCertVerify := make(map[string]any)
	for _, rule := range config.CertVerify {
		for _, pattern := range rule.Patterns {
			userCertVerify[pattern] = rule.Verify
		}
	}

	// Merge user rules with base rules, handling __AUTO__
	mergeRulesWithOverride(baseRules, userAlterHostname, userCertVerify, make(map[string]string))

	globalEngine.mu.Lock()
	globalEngine.rules = baseRules
	globalEngine.mu.Unlock()
	LogInfo("CORE: Rules updated (%d alter rules, %d cert verify rules)", len(userAlterHostname), len(userCertVerify))
	return nil
}

func FetchRemote(urlStr string) (string, error) {
	LogInfo("CORE: FetchRemote called for %s", urlStr)

	currentURL := urlStr
	client := &http.Client{
		CheckRedirect: func(req *http.Request, via []*http.Request) error {
			return http.ErrUseLastResponse // Handle redirects manually
		},
		Timeout: 10 * time.Second,
	}

	for i := 0; i < 10; i++ {
		// Parse URL to extract hostname
		u, err := url.Parse(currentURL)
		if err != nil {
			LogError("FetchRemote: Failed to parse URL %s: %v", currentURL, err)
			return "", err
		}
		hostname := u.Hostname()

		var targetSNI, targetIP string
		rule := globalEngine.Match(hostname)
		if rule != nil {
			if rule.TargetSNI != nil {
				targetSNI = *rule.TargetSNI
				LogInfo("FetchRemote: Using rule SNI: %s for %s", targetSNI, hostname)
			} else {
				targetSNI = hostname
			}
			if rule.TargetIP != nil {
				targetIP = *rule.TargetIP
				LogInfo("FetchRemote: Using rule IP: %s for %s", targetIP, hostname)
			}
		} else {
			targetSNI = hostname
		}

		dialer := getProtectedDialer()
		transport := &http.Transport{
			DialContext: func(ctx context.Context, network, addr string) (net.Conn, error) {
				actualAddr := addr
				if targetIP != "" {
					_, port, err := net.SplitHostPort(addr)
					if err == nil {
						actualAddr = net.JoinHostPort(targetIP, port)
					} else {
						actualAddr = net.JoinHostPort(targetIP, "443")
					}
					LogDebug("FetchRemote: Overriding IP %s -> %s", addr, actualAddr)
				}
				return dialer.DialContext(ctx, network, actualAddr)
			},
			TLSClientConfig: &tls.Config{
				ServerName:         targetSNI,
				InsecureSkipVerify: true,
			},
		}
		client.Transport = transport

		resp, err := client.Get(currentURL)
		if err != nil {
			LogError("FetchRemote: Get failed for %s: %v", currentURL, err)
			return "", err
		}

		if resp.StatusCode >= 300 && resp.StatusCode < 400 {
			location := resp.Header.Get("Location")
			resp.Body.Close()
			if location == "" {
				return "", fmt.Errorf("redirect without location")
			}
			// Resolve relative URL
			nextURL, err := u.Parse(location)
			if err != nil {
				return "", err
			}
			currentURL = nextURL.String()
			LogInfo("FetchRemote: Redirecting to %s", currentURL)
			continue
		}

		if resp.StatusCode != http.StatusOK {
			resp.Body.Close()
			return "", fmt.Errorf("HTTP %s", resp.Status)
		}

		body, err := io.ReadAll(resp.Body)
		resp.Body.Close()
		if err != nil {
			return "", err
		}

		LogInfo("FetchRemote: Success, read %d bytes", len(body))
		return string(body), nil
	}

	return "", fmt.Errorf("too many redirects")
}
