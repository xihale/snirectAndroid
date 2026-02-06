package core

import (
	"context"
	"encoding/binary"
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

	actualTarget := targetAddr
	if sni != "" {
		if rule := globalEngine.Match(sni); rule != nil {
			if rule.TargetSNI != "" {
				if mod := tryModifySNI(buf[:n], sni, rule.TargetSNI); mod != nil {
					data = mod
					if cb != nil {
						cb.OnStatusChanged("SNI: " + sni + " -> " + rule.TargetSNI)
					}
				}
			}
			if rule.TargetIP != "" {
				host, port, _ := net.SplitHostPort(targetAddr)
				if port == "" {
					port = "443"
				}

				resolvedIP := rule.TargetIP
				if net.ParseIP(rule.TargetIP) == nil {
					globalEngine.mu.RLock()
					resolver := globalEngine.resolver
					globalEngine.mu.RUnlock()
					if resolver != nil {
						if ip, err := resolver.Resolve(context.Background(), rule.TargetIP); err == nil {
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
	}

	remote, err := net.DialTimeout("tcp", actualTarget, 5*time.Second)
	if err != nil {
		return
	}
	defer remote.Close()

	remote.Write(data)
	go io.Copy(remote, localConn)
	io.Copy(localConn, remote)
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
