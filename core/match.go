package core

import (
	"path"
	"strings"
)

func MatchPattern(pattern, host string) bool {
	host = strings.ToLower(strings.TrimSpace(strings.TrimSuffix(host, ".")))
	pattern = strings.ToLower(strings.TrimSpace(strings.TrimSuffix(pattern, ".")))

	pattern = strings.Trim(pattern, "\"")
	pattern = strings.Trim(pattern, "'")
	pattern = strings.TrimSpace(pattern)

	if pattern == "" || host == "" {
		return false
	}

	if pattern == "*" || pattern == ".*" {
		return true
	}

	if strings.HasPrefix(pattern, "$") {
		return host == strings.TrimPrefix(pattern, "$")
	}

	if strings.HasPrefix(pattern, "*.") {
		domain := pattern[2:]
		return host == domain || strings.HasSuffix(host, "."+domain)
	}

	if strings.HasPrefix(pattern, "*") {
		return strings.HasSuffix(host, pattern[1:])
	}
	if strings.HasSuffix(pattern, "*") {
		return strings.HasPrefix(host, pattern[:len(pattern)-1])
	}

	if strings.Contains(pattern, "*") {
		matched, _ := path.Match(pattern, host)
		return matched
	}

	return host == pattern
}
