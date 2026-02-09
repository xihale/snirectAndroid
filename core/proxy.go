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
	"strings"
	"time"
)

func handleProxyConnection(localConn net.Conn, targetAddr string, cb EngineCallbacks) {
	if localConn == nil {
		LogError("handleProxyConnection: localConn is nil")
		return
	}

	defer func() {
		if r := recover(); r != nil {
			LogError("PANIC in handleProxyConnection: %v", r)
		}
		localConn.Close()
	}()

	if targetAddr == "" {
		LogWarn("handleProxyConnection: targetAddr is empty")
		return
	}

	buf := make([]byte, 4096)
	localConn.SetReadDeadline(time.Now().Add(3 * time.Second))
	n, err := localConn.Read(buf)
	if err != nil {
		LogDebug("HTTPS handshake read failed for %s: %v", targetAddr, err)
		return
	}
	localConn.SetReadDeadline(time.Time{})

	LogDebug("HTTPS: Read %d bytes from %s, first 16 bytes: %x", n, targetAddr, buf[:min(n, 16)])
	sni, sniErr := parseSNI(buf[:n])
	data := buf[:n]

	if sniErr != nil {
		LogDebug("HTTPS: SNI parse error for %s: %v", targetAddr, sniErr)
	} else {
		LogDebug("HTTPS: Parsed SNI '%s' for %s", sni, targetAddr)
	}

	if sni == "" && targetAddr != "" {
		host, _, _ := net.SplitHostPort(targetAddr)
		sni = host
		if sniErr != nil {
			LogDebug("SNI parse failed for %s, using host from target: %v", targetAddr, sniErr)
		}
	}

	// Peek rule match to see if interception is needed
	var matchedRule *Rule
	if sni != "" {
		matchedRule = globalEngine.Match(sni)
	}

	actualTarget := targetAddr
	var targetSNI string = sni
	var shouldMITM bool = false

	if matchedRule != nil {
		shouldMITM = true
		if matchedRule.TargetSNI != nil {
			targetSNI = *matchedRule.TargetSNI
			if targetSNI == "" {
				LogInfo("HTTPS SNI: %s -> <STRIP>", sni)
			} else {
				LogInfo("HTTPS SNI: %s -> %s", sni, targetSNI)
			}
		}
		if matchedRule.TargetIP != nil && *matchedRule.TargetIP != "" {
			host, port, err := net.SplitHostPort(targetAddr)
			if err != nil {
				host = targetAddr
				port = "443"
			}

			resolvedIP := *matchedRule.TargetIP
			if net.ParseIP(*matchedRule.TargetIP) == nil {
				globalEngine.mu.RLock()
				resolver := globalEngine.resolver
				globalEngine.mu.RUnlock()
				if resolver != nil {
					if ip, err := resolver.Resolve(context.Background(), *matchedRule.TargetIP); err == nil {
						resolvedIP = ip
					}
				}
			}

			if strings.Contains(resolvedIP, ":") && !strings.HasPrefix(resolvedIP, "[") {
				actualTarget = "[" + resolvedIP + "]:" + port
			} else {
				actualTarget = net.JoinHostPort(resolvedIP, port)
			}
			LogInfo("HTTPS Redirect: %s -> %s", host, actualTarget)
		} else {
			// No explicit TargetIP, but we matched a rule.
			// Re-resolve the SNI using trusted DNS to bypass potential DNS pollution.
			globalEngine.mu.RLock()
			resolver := globalEngine.resolver
			globalEngine.mu.RUnlock()
			if resolver != nil {
				LogDebug("Re-resolving SNI '%s' using trusted DNS", sni)
				if ip, err := resolver.Resolve(context.Background(), sni); err == nil {
					_, port, err := net.SplitHostPort(targetAddr)
					if err != nil {
						port = "443"
					}
					if strings.Contains(ip, ":") && !strings.HasPrefix(ip, "[") {
						actualTarget = "[" + ip + "]:" + port
					} else {
						actualTarget = net.JoinHostPort(ip, port)
					}
					LogInfo("HTTPS Re-resolved: %s -> %s (Trusted DNS)", sni, actualTarget)
				} else {
					LogWarn("Failed to re-resolve '%s': %v. Using original IP.", sni, err)
				}
			}
		}
	} else if sni != "" {
		LogInfo("HTTPS Direct: %s", sni)
	}

	if shouldMITM {
		// MITM Mode
		if certManager == nil {
			LogError("MITM requested but certManager not available for %s. DROPPING connection to prevent SNI leak.", sni)
			return
		}

		if targetSNI == "" {
			LogDebug("TLS Client: Stripping SNI extension for connection to %s", actualTarget)
		} else {
			LogDebug("TLS Client: Setting SNI to '%s' for connection to %s", targetSNI, actualTarget)
		}

		certBytes, key, err := certManager.SignLeafCert([]string{sni})
		if err != nil {
			LogError("Failed to sign cert for %s: %v", sni, err)
			return
		}
		LogDebug("Signed leaf cert for %s, len: %d", sni, len(certBytes))

		certPEM := pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: certBytes})
		keyPEM := pemEncodeKey(key)
		if len(certPEM) == 0 || len(keyPEM) == 0 {
			LogError("Failed to PEM encode cert or key for %s", sni)
			return
		}

		cert, err := tls.X509KeyPair(certPEM, keyPEM)
		if err != nil {
			LogError("Failed to create X509KeyPair for %s: %v (CertLen: %d, KeyLen: %d)", sni, err, len(certPEM), len(keyPEM))
			return
		}

		tlsConfig := &tls.Config{
			Certificates: []tls.Certificate{cert},
		}

		prefixConn := &PrefixConn{Conn: localConn, Prefix: data}
		tlsLocal := tls.Server(prefixConn, tlsConfig)

		if err := tlsLocal.Handshake(); err != nil {
			LogError("Client TLS handshake failed for %s: %v", sni, err)
			return
		}

		dialer := getProtectedDialer()
		rawRemote, err := dialer.Dial("tcp", actualTarget)
		if err != nil {
			LogError("Failed to dial %s: %v", actualTarget, err)
			return
		}

		remoteTLSConfig := &tls.Config{
			ServerName: targetSNI,
		}

		verify := matchedRule.CertVerify
		if verify == nil {
			verify = globalEngine.MatchCertVerify(sni)
		}

		if verify != nil {
			switch v := verify.(type) {
			case bool:
				if !v {
					LogInfo("TLS Client: Verification DISABLED for %s", sni)
					remoteTLSConfig.InsecureSkipVerify = true
				}
			case string:
				vLower := strings.ToLower(v)
				if vLower == "false" {
					LogInfo("TLS Client: Verification DISABLED for %s", sni)
					remoteTLSConfig.InsecureSkipVerify = true
				} else if vLower == "strict" || vLower == "true" {
					LogInfo("TLS Client: Strict verification for %s (using SNI %s)", sni, targetSNI)
				} else {
					LogInfo("TLS Client: Loose verification for %s (trusting SNI %s)", sni, v)
					remoteTLSConfig.InsecureSkipVerify = true
					remoteTLSConfig.VerifyConnection = func(cs tls.ConnectionState) error {
						opts := x509.VerifyOptions{
							DNSName:       v,
							Intermediates: x509.NewCertPool(),
						}
						for _, cert := range cs.PeerCertificates[1:] {
							opts.Intermediates.AddCert(cert)
						}
						_, err := cs.PeerCertificates[0].Verify(opts)
						return err
					}
				}
			}
		} else {
			remoteTLSConfig.InsecureSkipVerify = true
		}

		tlsRemote := tls.Client(rawRemote, remoteTLSConfig)

		if err := tlsRemote.Handshake(); err != nil {
			LogError("Server TLS handshake failed for %s (SNI: %s): %v", actualTarget, targetSNI, err)
			state := tlsRemote.ConnectionState()
			if len(state.PeerCertificates) > 0 {
				cert := state.PeerCertificates[0]
				LogError("Remote Certificate details: Subject='%s', Issuer='%s', DNSNames=%v, NotAfter=%s",
					cert.Subject, cert.Issuer, cert.DNSNames, cert.NotAfter)
			} else {
				LogError("No remote certificate received from %s (Handshake aborted by server or before cert exchange)", actualTarget)
			}
			rawRemote.Close()
			return
		}

		LogDebug("MITM tunnel established: %s -> %s (SNI: %s)", sni, actualTarget, targetSNI)
		go io.Copy(tlsRemote, tlsLocal)
		io.Copy(tlsLocal, tlsRemote)

	} else {
		forwardDirect(localConn, actualTarget, data)
	}
}

func handleUDPForwardDirect(localConn net.Conn, targetAddr string) {
	defer func() {
		if r := recover(); r != nil {
			LogError("PANIC in handleUDPForwardDirect: %v", r)
		}
		localConn.Close()
	}()

	dialer := getProtectedDialer()
	remote, err := dialer.Dial("udp", targetAddr)
	if err != nil {
		return
	}
	defer remote.Close()

	go io.Copy(remote, localConn)
	io.Copy(localConn, remote)
}

func forwardDirect(localConn net.Conn, targetAddr string, prefixData []byte) {
	dialer := getProtectedDialer()
	remote, err := dialer.Dial("tcp", targetAddr)
	if err != nil {
		LogError("Failed to connect to %s: %v", targetAddr, err)
		return
	}
	defer remote.Close()

	if len(prefixData) > 0 {
		if _, err := remote.Write(prefixData); err != nil {
			LogError("Failed to write prefix data to %s: %v", targetAddr, err)
			return
		}
	}

	done := make(chan error, 2)
	go func() {
		_, err := io.Copy(remote, localConn)
		done <- err
	}()
	go func() {
		_, err := io.Copy(localConn, remote)
		done <- err
	}()

	if err := <-done; err != nil {
		LogDebug("Connection closed for %s: %v", targetAddr, err)
	}
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

func min(a, b int) int {
	if a < b {
		return a
	}
	return b
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
