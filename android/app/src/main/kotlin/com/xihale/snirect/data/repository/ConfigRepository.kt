package com.xihale.snirect.data.repository

import android.content.Context
import android.os.Environment
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.TomlOutputConfig
import com.xihale.snirect.data.model.Rule
import com.xihale.snirect.data.model.RuleConfig
import com.xihale.snirect.data.model.TomlRuleConfig
import com.xihale.snirect.data.model.CertVerifyRule
import com.xihale.snirect.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URL

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class RuleWithSource(
    val rule: Rule,
    val isOverwrite: Boolean
)

class ConfigRepository(private val context: Context) {
    private val localRulesFile = File(context.filesDir, "rules.toml")
    private val fetchedRulesFile = File(context.filesDir, "fetched_rules.toml")
    
    private val toml = Toml(
        inputConfig = TomlInputConfig(ignoreUnknownNames = true),
        outputConfig = TomlOutputConfig()
    )

    companion object {
        val KEY_NAMESERVERS = stringPreferencesKey("nameservers")
        val KEY_BOOTSTRAP_DNS = stringPreferencesKey("bootstrap_dns")
        val KEY_CHECK_HOSTNAME = booleanPreferencesKey("check_hostname")
        
        val KEY_UPDATE_URL = stringPreferencesKey("update_url")
        val KEY_MTU = intPreferencesKey("mtu")
        val KEY_ENABLE_IPV6 = booleanPreferencesKey("enable_ipv6")
        val KEY_LOG_LEVEL = stringPreferencesKey("log_level")
        val KEY_DNS_SERVER = stringPreferencesKey("dns_server") // Legacy/Fallback
        val KEY_ACTIVATE_ON_STARTUP = booleanPreferencesKey("activate_on_startup")
        val KEY_ACTIVATE_ON_BOOT = booleanPreferencesKey("activate_on_boot")
        val KEY_HAS_SHOWN_HELP = booleanPreferencesKey("has_shown_help")
        val KEY_SKIP_CERT_CHECK = booleanPreferencesKey("skip_cert_check")
        
        const val DEFAULT_NAMESERVERS = "https://dnschina1.soraharu.com/dns-query,https://77.88.8.8/dns-query,https://dns.google/dns-query"
        const val DEFAULT_BOOTSTRAP_DNS = "tls://223.5.5.5"
        const val DEFAULT_UPDATE_URL = "https://github.com/SpaceTimee/Cealing-Host/releases/download/1.1.4.41/Cealing-Host.toml"
    }

    // Settings Operations

    val nameservers: Flow<List<String>> = context.dataStore.data.map { prefs ->
        (prefs[KEY_NAMESERVERS] ?: DEFAULT_NAMESERVERS).split(",").filter { it.isNotBlank() }
    }
    
    val bootstrapDns: Flow<List<String>> = context.dataStore.data.map { prefs ->
        (prefs[KEY_BOOTSTRAP_DNS] ?: DEFAULT_BOOTSTRAP_DNS).split(",").filter { it.isNotBlank() }
    }
    
    val checkHostname: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_CHECK_HOSTNAME] ?: true 
    }

    val updateUrl: Flow<String> = context.dataStore.data.map { it[KEY_UPDATE_URL] ?: DEFAULT_UPDATE_URL }
    val mtu: Flow<Int> = context.dataStore.data.map { it[KEY_MTU] ?: 1500 }
    val enableIpv6: Flow<Boolean> = context.dataStore.data.map { it[KEY_ENABLE_IPV6] ?: false }
    val logLevel: Flow<String> = context.dataStore.data.map { it[KEY_LOG_LEVEL] ?: "debug" }
    val dnsServer: Flow<String> = context.dataStore.data.map { it[KEY_DNS_SERVER] ?: "1.1.1.1" } // Legacy
    val activateOnStartup: Flow<Boolean> = context.dataStore.data.map { it[KEY_ACTIVATE_ON_STARTUP] ?: true }
    val activateOnBoot: Flow<Boolean> = context.dataStore.data.map { it[KEY_ACTIVATE_ON_BOOT] ?: false }
    val hasShownHelp: Flow<Boolean> = context.dataStore.data.map { it[KEY_HAS_SHOWN_HELP] ?: false }
    val skipCertCheck: Flow<Boolean> = context.dataStore.data.map { it[KEY_SKIP_CERT_CHECK] ?: false }

    suspend fun setNameservers(servers: List<String>) = context.dataStore.edit { 
        it[KEY_NAMESERVERS] = servers.joinToString(",") 
    }
    suspend fun setBootstrapDns(dns: List<String>) = context.dataStore.edit { 
        it[KEY_BOOTSTRAP_DNS] = dns.joinToString(",") 
    }
    suspend fun setCheckHostname(check: Boolean) = context.dataStore.edit { it[KEY_CHECK_HOSTNAME] = check }
    
    suspend fun setUpdateUrl(url: String) = context.dataStore.edit { it[KEY_UPDATE_URL] = url }
    suspend fun setMtu(mtu: Int) = context.dataStore.edit { it[KEY_MTU] = mtu }
    suspend fun setEnableIpv6(enable: Boolean) = context.dataStore.edit { it[KEY_ENABLE_IPV6] = enable }
    suspend fun setLogLevel(level: String) = context.dataStore.edit { it[KEY_LOG_LEVEL] = level }
    suspend fun setDnsServer(dns: String) = context.dataStore.edit { it[KEY_DNS_SERVER] = dns }
    suspend fun setActivateOnStartup(enable: Boolean) = context.dataStore.edit { it[KEY_ACTIVATE_ON_STARTUP] = enable }
    suspend fun setActivateOnBoot(enable: Boolean) = context.dataStore.edit { it[KEY_ACTIVATE_ON_BOOT] = enable }
    suspend fun setHasShownHelp(shown: Boolean) = context.dataStore.edit { it[KEY_HAS_SHOWN_HELP] = shown }
    suspend fun setSkipCertCheck(skip: Boolean) = context.dataStore.edit { it[KEY_SKIP_CERT_CHECK] = skip }

    // Rules Operations


    suspend fun getAllRulesWithSource(): List<RuleWithSource> = withContext(Dispatchers.IO) {
        copyAssetsIfNeeded()
        val local = readRulesFromFile(localRulesFile).first.map { RuleWithSource(it, true) }
        val fetched = readRulesFromFile(fetchedRulesFile).first.map { RuleWithSource(it, false) }
        local + fetched
    }

    private fun copyAssetsIfNeeded() {
        try {
            context.assets.open("rules.toml").use { input ->
                localRulesFile.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            AppLogger.e("Failed to copy rules.toml", e)
        }

        if (!fetchedRulesFile.exists()) {
            try {
                context.assets.open("fetched_rules.toml").use { input ->
                    fetchedRulesFile.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (e: Exception) {
                AppLogger.e("Failed to copy fetched_rules.toml", e)
            }
        }
    }

    suspend fun getMergedRules(): List<Rule> = withContext(Dispatchers.IO) {
        copyAssetsIfNeeded()
        val local = readRulesFromFile(localRulesFile).first.sortedByDescending { !it.targetSni.isNullOrBlank() }
        val fetched = readRulesFromFile(fetchedRulesFile).first.sortedByDescending { !it.targetSni.isNullOrBlank() }
        local + fetched
    }

    suspend fun getMergedCertVerify(): List<CertVerifyRule> = withContext(Dispatchers.IO) {
        copyAssetsIfNeeded()
        val local = readRulesFromFile(localRulesFile).second
        val fetched = readRulesFromFile(fetchedRulesFile).second
        local + fetched
    }

    private fun readRulesFromFile(file: File): Pair<List<Rule>, List<CertVerifyRule>> {
        if (!file.exists()) {
            AppLogger.w("Rules file does not exist: ${file.absolutePath}")
            return Pair(emptyList(), emptyList())
        }
        return try {
            var content = file.readText()
            
            // Pre-process TOML to quote keys with dots and bare boolean/unquoted values
            var inCertVerify = false
            val processedLines = content.lines().map { line ->
                val trimmed = line.trim()
                if (trimmed == "[cert_verify]") {
                    inCertVerify = true
                    return@map line
                }
                if (trimmed.startsWith("[")) {
                    inCertVerify = false
                }
                
                if (trimmed.contains("=") && !trimmed.startsWith("#")) {
                    val parts = line.split("=", limit = 2)
                    val key = parts[0].trim()
                    val value = parts[1].trim()
                    
                    var newKey = key
                    var newValue = value
                    
                    // Quote key if it contains dots and isn't quoted
                    if (key.contains(".") && !key.startsWith("\"") && !key.startsWith("'")) {
                        newKey = "\"$key\""
                    }
                    
                    // If in cert_verify, quote bare values (bools, strings without quotes)
                    if (inCertVerify && !value.startsWith("\"") && !value.startsWith("'") && !value.startsWith("[")) {
                        newValue = "\"$value\""
                    }
                    
                    if (newKey != key || newValue != value) {
                        return@map "$newKey =$newValue"
                    }
                }
                line
            }
            content = processedLines.joinToString("\n")

            AppLogger.d("Reading rules from ${file.name}, content length: ${content.length}")
            if (content.isBlank()) return Pair(emptyList(), emptyList())
            
            return try {
                val tomlConfig = toml.decodeFromString(TomlRuleConfig.serializer(), content)
                val rules = tomlConfig.toRulesList()
                AppLogger.i("Parsed ${rules.size} rules from TOML (v2): ${file.name}")
                Pair(rules, emptyList())
            } catch (e: Exception) {
                AppLogger.d("TOML v2 parse failed for ${file.name}, trying legacy: ${e.message}")
                try {
                    val legacyConfig = toml.decodeFromString(RuleConfig.serializer(), content)
                    val rules = legacyConfig.getRulesList()
                    AppLogger.i("Parsed ${rules.size} rules from TOML (legacy): ${file.name}")
                    Pair(rules, emptyList())
                } catch (e2: Exception) {
                    AppLogger.w("Failed to parse TOML in ${file.name}: ${e2.message}")
                    Pair(emptyList(), emptyList())
                }
            }
        } catch (e: Exception) {
            AppLogger.e("Failed to read rules from ${file.name}", e)
            Pair(emptyList(), emptyList())
        }
    }

    suspend fun saveLocalRules(rules: List<Rule>) = withContext(Dispatchers.IO) {
        writeRulesToFile(localRulesFile, rules)
    }

    suspend fun saveFetchedRules(rules: List<Rule>) = withContext(Dispatchers.IO) {
        writeRulesToFile(fetchedRulesFile, rules)
    }

    private fun writeRulesToFile(file: File, rules: List<Rule>) {
        try {
            val config = RuleConfig(rules)
            val content = toml.encodeToString(RuleConfig.serializer(), config)
            file.writeText(content)
        } catch (e: Exception) {
            AppLogger.e("Failed to save TOML rules to ${file.name}", e)
        }
    }

    suspend fun fetchRemoteRules(urlStr: String): List<Rule> = withContext(Dispatchers.IO) {
        try {
            val content = URL(urlStr).readText()
            
            val rules = if (urlStr.endsWith(".toml", ignoreCase = true)) {
                AppLogger.i("Fetching TOML rules from: $urlStr")
                val tomlConfig = toml.decodeFromString(TomlRuleConfig.serializer(), content)
                tomlConfig.toRulesList()
            } else if (urlStr.endsWith(".json", ignoreCase = true)) {
                AppLogger.i("Fetching JSON rules from: $urlStr")
                val jsonArray = Json.parseToJsonElement(content).jsonArray
                jsonArray.mapNotNull { parseJsonRule(it) }
            } else {
                try {
                    val tomlConfig = toml.decodeFromString(TomlRuleConfig.serializer(), content)
                    tomlConfig.toRulesList()
                } catch (e: Exception) {
                    AppLogger.w("TOML parse failed, trying JSON")
                    val jsonArray = Json.parseToJsonElement(content).jsonArray
                    jsonArray.mapNotNull { parseJsonRule(it) }
                }
            }
            
            saveFetchedRules(rules)
            AppLogger.i("Fetched and saved ${rules.size} rules")
            rules
        } catch (e: Exception) {
            AppLogger.e("Failed to fetch remote rules from $urlStr", e)
            throw e
        }
    }

    private fun parseJsonRule(element: JsonElement): Rule? {
        return try {
            val array = element.jsonArray
            if (array.size < 3) return null
            val patterns = array[0].jsonArray.map { it.jsonPrimitive.content }
            val sni = array[1].jsonPrimitive.content
            val ip = array[2].jsonPrimitive.content
            Rule(patterns, sni, ip)
        } catch (e: Exception) {
            null
        }
    }
}
