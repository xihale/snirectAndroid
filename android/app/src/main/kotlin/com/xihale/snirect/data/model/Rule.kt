package com.xihale.snirect.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class Rule(
    @SerialName("patterns") val patterns: List<String> = emptyList(),
    @SerialName("target_sni") val targetSni: String = "",
    @SerialName("target_ip") val targetIp: String = ""
)

@Serializable
data class RuleConfig(
    val rules: List<Rule> = emptyList()
)

@Serializable
data class CoreConfig(
    @SerialName("rules") val rules: List<Rule>,
    @SerialName("dns_server") val dnsServer: String
)
