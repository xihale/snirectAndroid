package com.xihale.snirect.data.repository

import android.content.Context
import android.util.Log
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
        
        const val DEFAULT_NAMESERVERS = "https://dnschina1.soraharu.com/dns-query,https://77.88.8.8/dns-query,https://dns.google/dns-query"
        const val DEFAULT_BOOTSTRAP_DNS = "tls://223.5.5.5"
        const val DEFAULT_UPDATE_URL = "https://github.com/SpaceTimee/Cealing-Host/raw/refs/heads/main/Cealing-Host.json"
    }

    // Settings Operations

    val nameservers: Flow<List<String>> = context.dataStore.data.map { prefs ->
        (prefs[KEY_NAMESERVERS] ?: DEFAULT_NAMESERVERS).split(",").filter { it.isNotBlank() }
    }
    
    val bootstrapDns: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_BOOTSTRAP_DNS] ?: DEFAULT_BOOTSTRAP_DNS
    }
    
    val checkHostname: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_CHECK_HOSTNAME] ?: true 
    }

    val updateUrl: Flow<String> = context.dataStore.data.map { it[KEY_UPDATE_URL] ?: DEFAULT_UPDATE_URL }
    val mtu: Flow<Int> = context.dataStore.data.map { it[KEY_MTU] ?: 1500 }
    val enableIpv6: Flow<Boolean> = context.dataStore.data.map { it[KEY_ENABLE_IPV6] ?: false }
    val logLevel: Flow<String> = context.dataStore.data.map { it[KEY_LOG_LEVEL] ?: "info" }
    val dnsServer: Flow<String> = context.dataStore.data.map { it[KEY_DNS_SERVER] ?: "1.1.1.1" } // Legacy

    suspend fun setNameservers(servers: List<String>) = context.dataStore.edit { 
        it[KEY_NAMESERVERS] = servers.joinToString(",") 
    }
    suspend fun setBootstrapDns(dns: String) = context.dataStore.edit { it[KEY_BOOTSTRAP_DNS] = dns }
    suspend fun setCheckHostname(check: Boolean) = context.dataStore.edit { it[KEY_CHECK_HOSTNAME] = check }
    
    suspend fun setUpdateUrl(url: String) = context.dataStore.edit { it[KEY_UPDATE_URL] = url }
    suspend fun setMtu(mtu: Int) = context.dataStore.edit { it[KEY_MTU] = mtu }
    suspend fun setEnableIpv6(enable: Boolean) = context.dataStore.edit { it[KEY_ENABLE_IPV6] = enable }
    suspend fun setLogLevel(level: String) = context.dataStore.edit { it[KEY_LOG_LEVEL] = level }
    suspend fun setDnsServer(dns: String) = context.dataStore.edit { it[KEY_DNS_SERVER] = dns }

    // Rules Operations

    suspend fun getAllRulesWithSource(): List<RuleWithSource> = withContext(Dispatchers.IO) {
        copyAssetsIfNeeded()
        val local = readRulesFromFile(localRulesFile).map { RuleWithSource(it, true) }
        val fetched = readRulesFromFile(fetchedRulesFile).map { RuleWithSource(it, false) }
        local + fetched
    }

    private fun copyAssetsIfNeeded() {
        if (!localRulesFile.exists()) {
            try {
                context.assets.open("rules.toml").use { input ->
                    localRulesFile.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (e: Exception) {
                Log.e("ConfigRepository", "Failed to copy default rules.toml", e)
            }
        }
        if (!fetchedRulesFile.exists()) {
            try {
                context.assets.open("fetched_rules.toml").use { input ->
                    fetchedRulesFile.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (e: Exception) {
                Log.e("ConfigRepository", "Failed to copy default fetched_rules.toml", e)
            }
        }
    }

    suspend fun getMergedRules(): List<Rule> = withContext(Dispatchers.IO) {
        val local = readRulesFromFile(localRulesFile)
        val fetched = readRulesFromFile(fetchedRulesFile)
        local + fetched
    }

    private fun readRulesFromFile(file: File): List<Rule> {
        if (!file.exists()) return emptyList()
        return try {
            val content = file.readText()
            if (content.isBlank()) return emptyList()
            toml.decodeFromString(RuleConfig.serializer(), content).rules
        } catch (e: Exception) {
            Log.e("ConfigRepository", "Failed to parse TOML rules from ${file.name}", e)
            emptyList()
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
            Log.e("ConfigRepository", "Failed to save TOML rules to ${file.name}", e)
        }
    }

    suspend fun fetchRemoteRules(urlStr: String): List<Rule> = withContext(Dispatchers.IO) {
        try {
            val jsonStr = URL(urlStr).readText()
            val jsonArray = Json.parseToJsonElement(jsonStr).jsonArray
            val rules = jsonArray.mapNotNull { parseJsonRule(it) }
            saveFetchedRules(rules)
            rules
        } catch (e: Exception) {
            Log.e("ConfigRepository", "Failed to fetch remote rules", e)
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
