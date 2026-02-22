package core

import (
	"encoding/json"
	"fmt"
	"sync"

	ruleslib "github.com/xihale/snirect-shared/rules"
)

type Rule struct {
	Patterns   []string `json:"patterns"`
	TargetSNI  *string  `json:"target_sni"`
	TargetIP   *string  `json:"target_ip"`
	CertVerify any      `json:"cert_verify"`
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
	mu       sync.RWMutex
	rules    *ruleslib.Rules
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

	rules, err := ruleslib.LoadRules()
	if err != nil {
		return nil, fmt.Errorf("failed to load base rules: %v", err)
	}

	userRules := ruleslib.NewRules()
	for _, rule := range config.Rules {
		for _, pattern := range rule.Patterns {
			if rule.TargetSNI != nil {
				userRules.AlterHostname[pattern] = *rule.TargetSNI
			}
			if rule.TargetIP != nil {
				userRules.Hosts[pattern] = *rule.TargetIP
			}
		}
	}

	for _, rule := range config.CertVerify {
		for _, pattern := range rule.Patterns {
			userRules.CertVerify[pattern] = rule.Verify
		}
	}

	ruleslib.ApplyOverrides(rules, userRules, ruleslib.DefaultAutoMarker)

	globalEngine.mu.Lock()
	globalEngine.rules = rules
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
		verifyDisplay := "nil"
		if r.CertVerify != nil {
			verifyDisplay = fmt.Sprintf("%v", r.CertVerify)
		}
		LogDebug("Rule[%d]: SNI=%s, IP=%s, Verify=%s, Patterns=%v", i, sniDisplay, ipDisplay, verifyDisplay, r.Patterns)
	}
	for i, r := range config.CertVerify {
		LogDebug("CertVerify[%d]: Verify=%v, Patterns=%v", i, r.Verify, r.Patterns)
	}
	return &config, nil
}

func (e *Engine) Match(sni string) *Rule {
	e.mu.RLock()
	defer e.mu.RUnlock()

	LogDebug("Engine: Matching SNI '%s' against rules", sni)

	targetSNI, ok := e.rules.GetAlterHostname(sni)
	if !ok {
		return nil
	}

	return &Rule{
		Patterns:  []string{sni},
		TargetSNI: &targetSNI,
	}
}

func (e *Engine) MatchCertVerify(sni string) any {
	e.mu.RLock()
	defer e.mu.RUnlock()

	LogDebug("Engine: Matching CertVerify for '%s' against rules", sni)

	certPolicy, ok := e.rules.GetCertVerify(sni)
	if !ok {
		return nil
	}

	// Convert CertPolicy to original format
	if certPolicy.Verify && len(certPolicy.Allow) == 0 {
		return true
	} else if !certPolicy.Verify && len(certPolicy.Allow) == 0 {
		return false
	} else if len(certPolicy.Allow) == 1 {
		return certPolicy.Allow[0]
	} else {
		// Convert []string to []interface{} for JSON compatibility
		result := make([]interface{}, len(certPolicy.Allow))
		for i, v := range certPolicy.Allow {
			result[i] = v
		}
		return result
	}
}
