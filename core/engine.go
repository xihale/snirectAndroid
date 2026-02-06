package core

import (
	"encoding/json"
	"fmt"
	"sync"
)

type Rule struct {
	Patterns  []string `json:"patterns"`
	TargetSNI string   `json:"target_sni"`
	TargetIP  string   `json:"target_ip"`
}

type Config struct {
	Rules        []Rule   `json:"rules"`
	NameServers  []string `json:"nameservers"`
	BootstrapDNS string   `json:"bootstrap_dns"`
	CheckHN      bool     `json:"check_hostname"`
	MTU          int      `json:"mtu"`
	EnableIPv6   bool     `json:"enable_ipv6"`
	LogLevel     string   `json:"log_level"`
}

type Engine struct {
	mu       sync.RWMutex
	rules    []Rule
	config   *Config
	resolver *Resolver
}

var globalEngine = &Engine{}

func InitEngine(jsonConfig string, cb EngineCallbacks) (*Config, error) {
	var config Config
	if err := json.Unmarshal([]byte(jsonConfig), &config); err != nil {
		return nil, fmt.Errorf("config parse error: %v", err)
	}

	globalEngine.mu.Lock()
	globalEngine.rules = config.Rules
	globalEngine.config = &config
	globalEngine.resolver = NewResolver(&config, cb)
	globalEngine.mu.Unlock()

	return &config, nil
}

func (e *Engine) Match(sni string) *Rule {
	e.mu.RLock()
	defer e.mu.RUnlock()

	for i := range e.rules {
		rule := &e.rules[i]
		for _, pattern := range rule.Patterns {
			if MatchPattern(pattern, sni) {
				return rule
			}
		}
	}
	return nil
}
