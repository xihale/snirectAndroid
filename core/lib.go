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

func StartEngine(fd int, configStr string, cb EngineCallbacks) {
	fmt.Printf("Snirect Core Starting... FD: %d\n", fd)

	caPath := "/data/data/com.xihale.snirect/files/ca.crt"
	keyPath := "/data/data/com.xihale.snirect/files/ca.key"

	var err error
	certManager, err = ca.NewCertManager(caPath, keyPath)
	if err != nil {
		fmt.Printf("Failed to init CA: %v\n", err)
	}

	config, err := InitEngine(configStr, cb)
	if err != nil {
		fmt.Printf("Failed to init engine: %v\n", err)
		// Proceed with empty/default? Or just log.
	}

	ts, err := NewTunStack(fd, config, cb)
	if err != nil {
		fmt.Printf("Failed to create TUN stack: %v\n", err)
		return
	}
	ts.Start()
}

func StopEngine() {
	fmt.Println("Snirect Core Stopped")
	if certManager != nil {
		certManager.Close()
	}
}

func GetCACertificate() []byte {
	if certManager != nil && certManager.RootCert != nil {
		return certManager.RootCert.Raw
	}

	caPath := "/data/data/com.xihale.snirect/files/ca.crt"
	keyPath := "/data/data/com.xihale.snirect/files/ca.key"
	cm, err := ca.NewCertManager(caPath, keyPath)
	if err == nil {
		certManager = cm
		return cm.RootCert.Raw
	}
	return nil
}
