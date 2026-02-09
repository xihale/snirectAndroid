package core

import (
	"encoding/json"
	"fmt"
	"strings"
	"sync"
)

type Rule struct {
	Patterns  []string `json:"patterns"`
	TargetSNI *string  `json:"target_sni"`
	TargetIP  *string  `json:"target_ip"`
}

type Config struct {
	Rules        []Rule   `json:"rules"`
	NameServers  []string `json:"nameservers"`
	BootstrapDNS []string `json:"bootstrap_dns"`
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
	cb       EngineCallbacks
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
	globalEngine.cb = cb
	SetLogLevel(config.LogLevel)
	globalEngine.resolver = NewResolver(&config, cb)
	globalEngine.mu.Unlock()

	LogInfo("Engine: Initialized with %d rules", len(config.Rules))
	for i, r := range config.Rules {
		sniDisplay := "<original>"
		if r.TargetSNI != nil {
			if *r.TargetSNI == "" {
				sniDisplay = "<strip>"
			} else {
				sniDisplay = *r.TargetSNI
			}
		}
		ipDisplay := "<original>"
		if r.TargetIP != nil {
			ipDisplay = *r.TargetIP
		}
		LogDebug("Rule[%d]: SNI=%s, IP=%s, Patterns=%v", i, sniDisplay, ipDisplay, r.Patterns)
	}
	return &config, nil
}

func (e *Engine) Match(sni string) *Rule {
	e.mu.RLock()
	defer e.mu.RUnlock()

	LogDebug("Engine: Matching SNI '%s' against %d rules", sni, len(e.rules))
	for i := range e.rules {
		rule := &e.rules[i]
		for _, pattern := range rule.Patterns {
			trimmedPattern := strings.Trim(strings.Trim(pattern, "\""), "'")
			if MatchPattern(trimmedPattern, sni) {
				targetSNI := ""
				if rule.TargetSNI != nil {
					targetSNI = *rule.TargetSNI
				}
				targetIP := ""
				if rule.TargetIP != nil {
					targetIP = *rule.TargetIP
				}
				LogInfo("Engine: Matched rule for '%s' with pattern '%s' (orig: '%s') -> SNI='%s', IP='%s'",
					sni, trimmedPattern, pattern, targetSNI, targetIP)
				return rule
			}
		}
	}
	return nil
}
