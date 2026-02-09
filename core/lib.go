package core

import (
	"encoding/json"
	"encoding/pem"
	"fmt"
	"log"
	"net"
	"syscall"
	"time"
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
