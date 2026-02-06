package core

import (
	"context"
	"crypto/ecdsa"
	"crypto/tls"
	"crypto/x509"
	"encoding/binary"
	"encoding/pem"
	"fmt"
	"io"
	"net"
	"time"
)

func handleProxyConnection(localConn net.Conn, targetAddr string, cb EngineCallbacks) {
	defer localConn.Close()
	buf := make([]byte, 4096)
	localConn.SetReadDeadline(time.Now().Add(3 * time.Second))
	n, err := localConn.Read(buf)
	if err != nil {
		return
	}
	localConn.SetReadDeadline(time.Time{})

	sni, _ := parseSNI(buf[:n])
	data := buf[:n]

	// Peek rule match to see if interception is needed
	var matchedRule *Rule
	if sni != "" {
		matchedRule = globalEngine.Match(sni)
	}

	actualTarget := targetAddr
	var targetSNI string = sni

	if matchedRule != nil {

		if matchedRule.TargetSNI != "" {
			targetSNI = matchedRule.TargetSNI
			if cb != nil {
				cb.OnStatusChanged("SNI: " + sni + " -> " + targetSNI)
			}
		}
		if matchedRule.TargetIP != "" {
			host, port, _ := net.SplitHostPort(targetAddr)
			if port == "" {
				port = "443"
			}
			resolvedIP := matchedRule.TargetIP
			if net.ParseIP(matchedRule.TargetIP) == nil {
				globalEngine.mu.RLock()
				resolver := globalEngine.resolver
				globalEngine.mu.RUnlock()
				if resolver != nil {
					if ip, err := resolver.Resolve(context.Background(), matchedRule.TargetIP); err == nil {
						resolvedIP = ip
					}
				}
			}
			actualTarget = net.JoinHostPort(resolvedIP, port)
			if cb != nil {
				cb.OnStatusChanged("Redirect: " + host + " -> " + resolvedIP)
			}
		}
	}

	if matchedRule != nil && matchedRule.TargetSNI != "" {

		// MITM Mode
		// 1. Sign a cert for the ORIGINAL SNI (so client trusts us)
		// 2. Dial remote with NEW SNI

		// Ensure certManager is available
		if certManager == nil {
			// Fallback to direct forwarding if CA not ready
			forwardDirect(localConn, actualTarget, data)
			return
		}

		// Generate Cert for 'sni' (the hostname the client thinks it's connecting to)
		certBytes, key, err := certManager.SignLeafCert([]string{sni})
		if err != nil {
			fmt.Printf("Failed to sign cert: %v\n", err)
			return
		}

		cert, err := tls.X509KeyPair(certBytes, pemEncodeKey(key))
		if err != nil {
			return
		}

		tlsConfig := &tls.Config{
			Certificates: []tls.Certificate{cert},
		}

		// Wrap local connection (server-side)
		// We already read 'data' (ClientHello). We need to replay it or use a prefix conn.
		// Go's tls.Server expects to read the ClientHello itself.
		// We can construct a PrefixConn that replays 'data'.
		prefixConn := &PrefixConn{Conn: localConn, Prefix: data}
		tlsLocal := tls.Server(prefixConn, tlsConfig)

		if err := tlsLocal.Handshake(); err != nil {
			// Handshake failed (maybe client didn't like our cert, or not TLS)
			// Fallback? No, just fail.
			return
		}

		// Dial Remote (client-side)
		// Use targetSNI for the remote handshake
		dialer := &net.Dialer{Timeout: 5 * time.Second}
		rawRemote, err := dialer.Dial("tcp", actualTarget)
		if err != nil {
			return
		}

		tlsRemote := tls.Client(rawRemote, &tls.Config{
			ServerName:         targetSNI,
			InsecureSkipVerify: true, // We accept whatever the backend gives us, usually
		})

		if err := tlsRemote.Handshake(); err != nil {
			rawRemote.Close()
			return
		}

		// Bridge
		go io.Copy(tlsRemote, tlsLocal)
		io.Copy(tlsLocal, tlsRemote)

	} else {
		// Direct forwarding (TCP Tunnel)
		// Used when no SNI modification is required
		forwardDirect(localConn, actualTarget, data)
	}
}

func forwardDirect(localConn net.Conn, targetAddr string, prefixData []byte) {
	remote, err := net.DialTimeout("tcp", targetAddr, 5*time.Second)
	if err != nil {
		return
	}
	defer remote.Close()

	if len(prefixData) > 0 {
		remote.Write(prefixData)
	}
	go io.Copy(remote, localConn)
	io.Copy(localConn, remote)
}

type PrefixConn struct {
	net.Conn
	Prefix []byte
}

func (c *PrefixConn) Read(b []byte) (int, error) {
	if len(c.Prefix) > 0 {
		n := copy(b, c.Prefix)
		c.Prefix = c.Prefix[n:]
		return n, nil
	}
	return c.Conn.Read(b)
}

func pemEncodeKey(key interface{}) []byte {
	b, _ := x509.MarshalECPrivateKey(key.(*ecdsa.PrivateKey))
	return pem.EncodeToMemory(&pem.Block{Type: "EC PRIVATE KEY", Bytes: b})
}

func parseSNI(data []byte) (string, error) {
	pos, err := findSNIPos(data)
	if err != nil {
		return "", err
	}
	if pos+3 > len(data) {
		return "", fmt.Errorf("overflow")
	}
	nameLen := int(binary.BigEndian.Uint16(data[pos+1 : pos+3]))
	if pos+3+nameLen > len(data) {
		return "", fmt.Errorf("overflow")
	}
	return string(data[pos+3 : pos+3+nameLen]), nil
}

func findSNIPos(data []byte) (int, error) {
	if len(data) < 43 {
		return 0, fmt.Errorf("short")
	}
	pos := 9 + 2 + 32
	sessionIDLen := int(data[pos])
	pos += 1 + sessionIDLen
	if pos+2 > len(data) {
		return 0, fmt.Errorf("overflow")
	}
	cipherSuitesLen := int(binary.BigEndian.Uint16(data[pos : pos+2]))
	pos += 2 + cipherSuitesLen
	if pos+1 > len(data) {
		return 0, fmt.Errorf("overflow")
	}
	compMethodsLen := int(data[pos])
	pos += 1 + compMethodsLen
	if pos+2 > len(data) {
		return 0, fmt.Errorf("overflow")
	}
	extLen := int(binary.BigEndian.Uint16(data[pos : pos+2]))
	pos += 2
	end := pos + extLen
	for pos+4 <= end && pos+4 <= len(data) {
		extType := binary.BigEndian.Uint16(data[pos : pos+2])
		extSize := int(binary.BigEndian.Uint16(data[pos+2 : pos+4]))
		pos += 4
		if extType == 0x00 {
			return pos + 2, nil
		}
		pos += extSize
	}
	return 0, fmt.Errorf("not found")
}

func tryModifySNI(data []byte, old, new string) []byte {
	pos, err := findSNIPos(data)
	if err != nil {
		return nil
	}
	nameLen := int(binary.BigEndian.Uint16(data[pos+1 : pos+3]))
	if len(new) == nameLen {
		newData := make([]byte, len(data))
		copy(newData, data)
		copy(newData[pos+3:], []byte(new))
		return newData
	}
	return nil
}
