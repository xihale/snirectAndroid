package com.xihale.snirect.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonElement

// New TOML configuration format (Cealing-Host style)
@Serializable
data class TomlRuleConfig(
    @SerialName("alter_hostname") val alterHostname: Map<String, String> = emptyMap(),
    @SerialName("cert_verify") val certVerify: Map<String, JsonElement> = emptyMap(),
    @SerialName("hosts") val hosts: Map<String, String> = emptyMap()
) {
    /**
     * Convert TOML format to legacy Rule format for Go core
     * 
     * Logic:
     * - [alter_hostname] provides pattern -> SNI mapping
     * - [hosts] provides pattern -> IP mapping
     * - Merge both: if pattern exists in both, combine into single Rule
     */
    fun toRulesList(): List<Rule> {
        val rules = mutableListOf<Rule>()
        val processedPatterns = mutableSetOf<String>()
        
        // Process [alter_hostname] entries
        for ((pattern, targetSni) in alterHostname) {
            val targetIp = hosts[pattern] ?: ""
            rules.add(Rule(
                patterns = listOf(pattern),
                targetSni = targetSni,
                targetIp = targetIp
            ))
            processedPatterns.add(pattern)
        }
        
        // Process remaining [hosts] entries not in [alter_hostname]
        for ((pattern, targetIp) in hosts) {
            if (pattern !in processedPatterns) {
                rules.add(Rule(
                    patterns = listOf(pattern),
                    targetSni = "", // No SNI alteration, use original
                    targetIp = targetIp
                ))
            }
        }
        
        return rules
    }
}
