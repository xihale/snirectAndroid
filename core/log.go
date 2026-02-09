package core

import (
	"fmt"
	"log"
	"sync"
)

type LogLevel int

const (
	LevelDebug LogLevel = iota
	LevelInfo
	LevelWarn
	LevelError
)

var (
	levelNames = map[string]LogLevel{
		"debug": LevelDebug,
		"info":  LevelInfo,
		"warn":  LevelWarn,
		"error": LevelError,
	}
	currentLogLevel = LevelInfo
	lastCb          EngineCallbacks
	cbMutex         sync.RWMutex
	logChan         = make(chan string, 100)
)

func init() {
	go func() {
		for msg := range logChan {
			cbMutex.RLock()
			cb := lastCb
			cbMutex.RUnlock()
			if cb != nil {
				func() {
					defer func() { recover() }()
					cb.OnStatusChanged(msg)
				}()
			}
		}
	}()
}

func SetLogLevel(levelStr string) {
	cbMutex.Lock()
	defer cbMutex.Unlock()
	if level, ok := levelNames[levelStr]; ok {
		currentLogLevel = level
	}
}

func logf(level LogLevel, format string, args ...interface{}) {
	if level < currentLogLevel {
		return
	}

	prefix := ""
	switch level {
	case LevelDebug:
		prefix = "[DEBUG] "
	case LevelInfo:
		prefix = "[INFO] "
	case LevelWarn:
		prefix = "[WARN] "
	case LevelError:
		prefix = "[ERROR] "
	}

	msg := prefix + fmt.Sprintf(format, args...)
	log.Println(msg)

	select {
	case logChan <- msg:
	default:
	}
}

func LogDebug(format string, args ...interface{}) { logf(LevelDebug, format, args...) }
func LogInfo(format string, args ...interface{})  { logf(LevelInfo, format, args...) }
func LogWarn(format string, args ...interface{})  { logf(LevelWarn, format, args...) }
func LogError(format string, args ...interface{}) { logf(LevelError, format, args...) }
