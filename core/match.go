package core

import (
	"path"
	"strings"
)

func MatchPattern(pattern, host string) bool {
	if strings.HasPrefix(pattern, "#") {
		return false
	}

	pattern = strings.TrimPrefix(pattern, "$")

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
