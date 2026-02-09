package com.xihale.snirect.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonElement

@Serializable
data class Rule(
    @SerialName("patterns") val patterns: List<String>? = null,
    @SerialName("target_sni") val targetSni: String? = null,
    @SerialName("target_ip") val targetIp: String? = null,
    @SerialName("cert_verify") val certVerify: String? = null
) {
    // Helper functions to get safe values
    fun patternsOrEmpty(): List<String> = patterns ?: emptyList()
    fun targetSniOrDefault(): String = targetSni ?: "" // Treat null as empty for some compat? NO.
    // If targetSni is null, it means ORIGINAL.
    // If targetSni is "", it means STRIP.
    // UI needs to know the difference.
}

@Serializable
data class CertVerifyRule(
    @SerialName("patterns") val patterns: List<String>,
    @SerialName("verify") val verify: JsonElement
)

@Serializable
data class RuleConfig(
    val rules: List<Rule>? = null
) {
    fun getRulesList(): List<Rule> = rules ?: emptyList()
}

@Serializable
data class CoreConfig(
    @SerialName("rules") val rules: List<Rule>? = null,
    @SerialName("cert_verify") val certVerify: List<CertVerifyRule>? = null,
    @SerialName("nameservers") val nameservers: List<String>? = null,
    @SerialName("bootstrap_dns") val bootstrapDns: List<String>? = null,
    @SerialName("check_hostname") val checkHostname: Boolean = false,
    @SerialName("mtu") val mtu: Int = 1500,
    @SerialName("enable_ipv6") val enableIpv6: Boolean = false,
    @SerialName("log_level") val logLevel: String = "info"
)
