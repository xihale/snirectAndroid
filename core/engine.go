package core

import (
	"encoding/json"
	"fmt"
	"sync"
)

type Rule struct {
	Patterns  []string `json:"patterns"`
	TargetSNI *string  `json:"target_sni"`
	TargetIP  *string  `json:"target_ip"`
}

type CertVerifyRule struct {
	Patterns []string `json:"patterns"`
	Verify   any      `json:"verify"`
}

type Config struct {
	Rules        []Rule           `json:"rules"`
	CertVerify   []CertVerifyRule `json:"cert_verify"`
	NameServers  []string         `json:"nameservers"`
	BootstrapDNS []string         `json:"bootstrap_dns"`
	CheckHN      bool             `json:"check_hostname"`
	MTU          int              `json:"mtu"`
	EnableIPv6   bool             `json:"enable_ipv6"`
	LogLevel     string           `json:"log_level"`
}

type Engine struct {
	mu         sync.RWMutex
	rules      []Rule
	certVerify []CertVerifyRule
	config     *Config
	resolver   *Resolver
	cb         EngineCallbacks
}

var globalEngine = &Engine{}

func InitEngine(jsonConfig string, cb EngineCallbacks) (*Config, error) {
	var config Config
	if err := json.Unmarshal([]byte(jsonConfig), &config); err != nil {
		return nil, fmt.Errorf("config parse error: %v", err)
	}

	globalEngine.mu.Lock()
	globalEngine.rules = config.Rules
	globalEngine.certVerify = config.CertVerify
	globalEngine.config = &config
	globalEngine.cb = cb
	SetLogLevel(config.LogLevel)
	globalEngine.resolver = NewResolver(&config, cb)
	globalEngine.mu.Unlock()

	LogInfo("Engine: Initialized with %d rules and %d cert verify rules", len(config.Rules), len(config.CertVerify))
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
	for i, r := range config.CertVerify {
		LogDebug("CertVerify[%d]: Verify=%v, Patterns=%v", i, r.Verify, r.Patterns)
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
			if MatchPattern(pattern, sni) {
				targetSNI := ""
				if rule.TargetSNI != nil {
					targetSNI = *rule.TargetSNI
				}
				targetIP := ""
				if rule.TargetIP != nil {
					targetIP = *rule.TargetIP
				}
				LogInfo("Engine: Matched rule for '%s' with pattern '%s' -> SNI='%s', IP='%s'",
					sni, pattern, targetSNI, targetIP)
				return rule
			}
		}
	}
	return nil
}

func (e *Engine) MatchCertVerify(sni string) any {
	e.mu.RLock()
	defer e.mu.RUnlock()

	LogDebug("Engine: Matching CertVerify for '%s' against %d rules", sni, len(e.certVerify))
	for i := range e.certVerify {
		rule := &e.certVerify[i]
		for _, pattern := range rule.Patterns {
			if MatchPattern(pattern, sni) {
				LogDebug("Engine: CertVerify matched '%s' with pattern '%s' -> %v", sni, pattern, rule.Verify)
				return rule.Verify
			}
		}
	}
	return nil
}
