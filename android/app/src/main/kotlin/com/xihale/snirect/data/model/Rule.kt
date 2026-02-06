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
    @SerialName("nameservers") val nameservers: List<String>,
    @SerialName("bootstrap_dns") val bootstrapDns: String,
    @SerialName("check_hostname") val checkHostname: Boolean = false,
    @SerialName("mtu") val mtu: Int = 1500,
    @SerialName("enable_ipv6") val enableIpv6: Boolean = false,
    @SerialName("log_level") val logLevel: String = "info"
)
