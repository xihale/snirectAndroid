package com.xihale.snirect.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

// New TOML configuration format (Cealing-Host style)
@Serializable
data class TomlRuleConfig(
    @SerialName("alter_hostname") val alterHostname: Map<String, String> = emptyMap(),
    @SerialName("cert_verify") val certVerify: Map<String, String> = emptyMap(),
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
            val cleanPattern = pattern.trim('"', '\'').trimStart('#', '$')
            val targetIp = hosts[pattern] ?: ""
            val verify = certVerify[pattern]
            
            rules.add(Rule(
                patterns = listOf(cleanPattern),
                targetSni = targetSni,
                targetIp = targetIp.ifEmpty { null },
                certVerify = verify
            ))
            processedPatterns.add(pattern)
        }
        
        // Process [hosts] entries not in [alter_hostname]
        for ((pattern, targetIp) in hosts) {
            if (pattern !in processedPatterns) {
                val cleanPattern = pattern.trim('"', '\'').trimStart('#', '$')
                val verify = certVerify[pattern]
                
                rules.add(Rule(
                    patterns = listOf(cleanPattern),
                    targetSni = "", // Default to strip for hosts-only rules (legacy behavior)
                    targetIp = targetIp,
                    certVerify = verify
                ))
                processedPatterns.add(pattern)
            }
        }
        
        // Process remaining [cert_verify] entries not in others
        for ((pattern, verify) in certVerify) {
             if (pattern !in processedPatterns) {
                val cleanPattern = pattern.trim('"', '\'').trimStart('#', '$')
                rules.add(Rule(
                    patterns = listOf(cleanPattern),
                    targetSni = null, // KEEP ORIGINAL SNI for cert-verify-only rules
                    targetIp = null,
                    certVerify = verify
                ))
             }
        }
        
        return rules
    }
}
