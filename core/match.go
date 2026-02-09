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

	pattern = strings.TrimLeft(pattern, "#$")

	if strings.HasPrefix(pattern, "*.") {
		domain := pattern[2:]
		if host == domain || strings.HasSuffix(host, "."+domain) {
			return true
		}
	}

	if matched, _ := path.Match(pattern, host); matched {
		return true
	}

	return host == pattern
}
