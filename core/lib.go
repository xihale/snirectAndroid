package core

import (
	"fmt"
)

type EngineCallbacks interface {
	OnStatusChanged(status string)
	OnSpeedUpdated(up int64, down int64)
}

func StartEngine(fd int, config string, cb EngineCallbacks) {
	fmt.Printf("Snirect Core Starting... FD: %d\n", fd)
	ts, _ := NewTunStack(fd, cb)
	ts.Start()
}

func StopEngine() {
	fmt.Println("Snirect Core Stopped")
}

func GetCACertificate() []byte {
	return nil
}
