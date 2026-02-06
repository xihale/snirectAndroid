package core

import (
	"encoding/json"
	"fmt"
	"net"
	"regexp"
	"strings"
	"sync"
)

// RequestMetadata 包含用于规则匹配的请求上下文
type RequestMetadata struct {
	SNI     string
	DstIP   string
	DstPort int
}

// Rule 定义单条规则
type Rule struct {
	// 匹配条件
	Type  string `json:"type"`  // "domain", "domain_suffix", "domain_regex", "cidr"
	Value string `json:"value"` // 匹配值

	// 执行动作
	Action string `json:"action"` // "snimod", "reject", "direct"
	Args   string `json:"args"`   // 动作参数 (例如替换后的 SNI)

	// 缓存/预编译字段 (私有)
	ipNet       *net.IPNet
	domainRegex *regexp.Regexp
}

// Engine 规则引擎单例
type Engine struct {
	mu    sync.RWMutex
	rules []Rule
}

var globalEngine = &Engine{}

// UpdateRules 动态加载并校验规则 (热更新入口)
func UpdateRules(jsonConfig string) error {
	var newRules []Rule
	if err := json.Unmarshal([]byte(jsonConfig), &newRules); err != nil {
		return fmt.Errorf("json parse error: %v", err)
	}

	// 数据校验与预编译
	for i := range newRules {
		r := &newRules[i]
		switch r.Type {
		case "cidr":
			_, ipNet, err := net.ParseCIDR(r.Value)
			if err != nil {
				return fmt.Errorf("invalid CIDR at rule %d: %v", i, err)
			}
			r.ipNet = ipNet
		case "domain_regex":
			re, err := regexp.Compile(r.Value)
			if err != nil {
				return fmt.Errorf("invalid Regex at rule %d: %v", i, err)
			}
			r.domainRegex = re
		case "domain", "domain_suffix":
			// 基础校验通过
		default:
			return fmt.Errorf("unknown rule type at rule %d: %s", i, r.Type)
		}
	}

	// 原子替换
	globalEngine.mu.Lock()
	globalEngine.rules = newRules
	globalEngine.mu.Unlock()
	
	fmt.Printf("Engine: Loaded %d rules successfully.
", len(newRules))
	return nil
}

// Match 核心匹配函数 (表达式求值)
func (e *Engine) Match(meta RequestMetadata) *Rule {
	e.mu.RLock()
	defer e.mu.RUnlock()

	for i := range e.rules {
		rule := &e.rules[i]
		matched := false

		switch rule.Type {
		case "domain":
			matched = (meta.SNI == rule.Value)
		case "domain_suffix":
			matched = strings.HasSuffix(meta.SNI, rule.Value)
		case "domain_regex":
			if rule.domainRegex != nil {
				matched = rule.domainRegex.MatchString(meta.SNI)
			}
		case "cidr":
			if rule.ipNet != nil {
				ip := net.ParseIP(meta.DstIP)
				matched = rule.ipNet.Contains(ip)
			}
		}

		if matched {
			return rule
		}
	}
	return nil
}
