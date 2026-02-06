package core

import (
	"fmt"
)

type EngineCallbacks interface {
	OnStatusChanged(status string)
	OnSpeedUpdated(up int64, down int64)
}

func StartEngine(fd int, configStr string, cb EngineCallbacks) {
	fmt.Printf("Snirect Core Starting... FD: %d\n", fd)

	config, err := InitEngine(configStr)
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
}

func GetCACertificate() []byte {
	return nil
}
