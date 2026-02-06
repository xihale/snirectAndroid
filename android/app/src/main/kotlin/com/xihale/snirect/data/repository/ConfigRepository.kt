package com.xihale.snirect.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.akuleshov7.ktoml.Toml
import com.xihale.snirect.data.model.Rule
import com.xihale.snirect.data.model.RuleConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import java.io.File
import java.net.URL

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class ConfigRepository(private val context: Context) {

    private val rulesFile = File(context.filesDir, "rules.toml")
    private val toml = Toml.partiallyLoose

    companion object {
        val KEY_DNS_SERVER = stringPreferencesKey("dns_server")
        val KEY_UPDATE_URL = stringPreferencesKey("update_url")
        const val DEFAULT_UPDATE_URL = "https://github.com/SpaceTimee/Cealing-Host/raw/refs/heads/main/Cealing-Host.json"
    }

    val dnsServer: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[KEY_DNS_SERVER] ?: "1.1.1.1" }

    val updateUrl: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[KEY_UPDATE_URL] ?: DEFAULT_UPDATE_URL }

    suspend fun setDnsServer(dns: String) {
        context.dataStore.edit { it[KEY_DNS_SERVER] = dns }
    }

    suspend fun setUpdateUrl(url: String) {
        context.dataStore.edit { it[KEY_UPDATE_URL] = url }
    }

    suspend fun getRules(): List<Rule> = withContext(Dispatchers.IO) {
        if (!rulesFile.exists()) {
            val defaultRules = RuleConfig(emptyList())
            saveRules(defaultRules.rules)
            return@withContext emptyList()
        }
        try {
            val content = rulesFile.readText()
            if (content.isBlank()) return@withContext emptyList()
            toml.decodeFromString(RuleConfig.serializer(), content).rules
        } catch (e: Exception) {
            Log.e("ConfigRepository", "Failed to parse TOML rules", e)
            emptyList()
        }
    }

    suspend fun saveRules(rules: List<Rule>) = withContext(Dispatchers.IO) {
        try {
            val config = RuleConfig(rules)
            val content = toml.encodeToString(RuleConfig.serializer(), config)
            rulesFile.writeText(content)
        } catch (e: Exception) {
            Log.e("ConfigRepository", "Failed to save TOML rules", e)
        }
    }

    suspend fun fetchRemoteRules(urlStr: String): List<Rule> = withContext(Dispatchers.IO) {
        try {
            val jsonStr = URL(urlStr).readText()
            val jsonArray = Json.parseToJsonElement(jsonStr).jsonArray
            
            // Parse: [ [patterns], sni, ip ]
            val rules = jsonArray.mapNotNull { element ->
                parseJsonRule(element)
            }
            rules
        } catch (e: Exception) {
            Log.e("ConfigRepository", "Failed to fetch remote rules", e)
            throw e
        }
    }

    private fun parseJsonRule(element: JsonElement): Rule? {
        try {
            // Expecting array: [ ["p1", "p2"], "sni", "ip" ]
            val array = element.jsonArray
            if (array.size < 3) return null

            val patterns = array[0].jsonArray.map { it.jsonPrimitive.content }
            val sni = array[1].jsonPrimitive.content
            val ip = array[2].jsonPrimitive.content

            return Rule(patterns, sni, ip)
        } catch (e: Exception) {
            return null
        }
    }
}
