package core

import (
	"fmt"
	"github.com/xihale/snirect/core/ca"
)

type EngineCallbacks interface {
	OnStatusChanged(status string)
	OnSpeedUpdated(up int64, down int64)
}

var certManager *ca.CertManager
var lastCb EngineCallbacks

func StartEngine(fd int, configStr string, cb EngineCallbacks) {
	lastCb = cb
	if cb != nil {
		cb.OnStatusChanged("CORE: Starting...")
	}

	// Use standard Android path (fallback if we can't get it dynamically)
	caPath := "/data/data/com.xihale.snirect/files/ca.crt"
	keyPath := "/data/data/com.xihale.snirect/files/ca.key"

	var err error
	certManager, err = ca.NewCertManager(caPath, keyPath)
	if err != nil {
		if cb != nil {
			cb.OnStatusChanged(fmt.Sprintf("CORE: CA Init Error: %v", err))
		}
	} else {
		if cb != nil {
			cb.OnStatusChanged("CORE: CA ready")
		}
	}

	config, err := InitEngine(configStr, cb)
	if err != nil {
		if cb != nil {
			cb.OnStatusChanged(fmt.Sprintf("CORE: Engine Init Error: %v", err))
		}
	}

	ts, err := NewTunStack(fd, config, cb)
	if err != nil {
		if cb != nil {
			cb.OnStatusChanged(fmt.Sprintf("CORE: TUN Setup Failed: %v", err))
		}
		return
	}
	ts.Start()
}

func StopEngine() {
	if lastCb != nil {
		lastCb.OnStatusChanged("CORE: Stopping...")
	}
	if certManager != nil {
		certManager.Close()
	}
}

func GetCACertificate() []byte {
	fmt.Println("CORE: GetCACertificate called")
	if certManager != nil && certManager.RootCert != nil {
		return certManager.RootCert.Raw
	}

	caPath := "/data/data/com.xihale.snirect/files/ca.crt"
	keyPath := "/data/data/com.xihale.snirect/files/ca.key"
	fmt.Printf("CORE: Loading CA from %s\n", caPath)

	cm, err := ca.NewCertManager(caPath, keyPath)
	if err == nil {
		certManager = cm
		return cm.RootCert.Raw
	}
	fmt.Printf("CORE: CA Load Failed: %v\n", err)
	return nil
}
