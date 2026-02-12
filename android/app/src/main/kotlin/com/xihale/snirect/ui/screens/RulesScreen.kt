package com.xihale.snirect.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.xihale.snirect.data.model.Rule
import com.xihale.snirect.data.repository.ConfigRepository
import com.xihale.snirect.data.repository.RuleWithSource
import com.xihale.snirect.ui.theme.AppIcons
import kotlinx.coroutines.launch

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.res.stringResource
import com.xihale.snirect.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    navController: NavController,
    repository: ConfigRepository
) {
    var rulesWithSource by remember { mutableStateOf<List<RuleWithSource>>(emptyList()) }
    var showDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf<Rule?>(null) }
    var showSyncDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var updateUrl by remember { mutableStateOf("") }
    
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        rulesWithSource = repository.getAllRulesWithSource()
        repository.updateUrl.collect { updateUrl = it }
    }

    val filteredRules = remember(rulesWithSource, searchQuery) {
        if (searchQuery.isEmpty()) rulesWithSource
        else rulesWithSource.filter { item ->
            val patterns = item.rule.patterns ?: emptyList()
            val targetSni = item.rule.targetSni ?: ""
            val targetIp = item.rule.targetIp ?: ""
            patterns.any { it.contains(searchQuery, ignoreCase = true) } ||
            targetSni.contains(searchQuery, ignoreCase = true) ||
            targetIp.contains(searchQuery, ignoreCase = true)
        }
    }

    fun saveRule(newRule: Rule, oldRule: Rule? = null) {
         scope.launch {
             val allLocalItems = repository.getAllRulesWithSource().filter { it.isOverwrite }
             val currentLocalRules = allLocalItems.map { it.rule }.toMutableList()
             
             if (oldRule != null) {
                 val index = currentLocalRules.indexOfFirst { 
                     it.patterns == oldRule.patterns && it.targetSni == oldRule.targetSni && it.targetIp == oldRule.targetIp 
                 }
                 if (index != -1) currentLocalRules[index] = newRule
             } else {
                 currentLocalRules.add(newRule)
             }
            repository.saveLocalRules(currentLocalRules)
            rulesWithSource = repository.getAllRulesWithSource().toList()
        }
    }

    fun deleteRule(rule: Rule) {
        scope.launch {
            val allLocalItems = repository.getAllRulesWithSource().filter { it.isOverwrite }
            val currentLocalRules = allLocalItems.map { it.rule }.toMutableList()
            
            val toRemove = currentLocalRules.find { 
                it.patterns == rule.patterns && it.targetSni == rule.targetSni && it.targetIp == rule.targetIp 
            }
            if (toRemove != null) {
                currentLocalRules.remove(toRemove)
                repository.saveLocalRules(currentLocalRules)
                rulesWithSource = repository.getAllRulesWithSource().toList()
            }
        }
    }

    fun fetchRules() {
        scope.launch {
            try {
                Toast.makeText(context, context.getString(R.string.toast_syncing), Toast.LENGTH_SHORT).show()
                repository.fetchRemoteRules(updateUrl)
                rulesWithSource = repository.getAllRulesWithSource().toList()
                Toast.makeText(context, context.getString(R.string.toast_sync_success), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {

                Toast.makeText(context, context.getString(R.string.toast_error, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.rules_title)) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSyncDialog = true }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.remote_rules_title))
                        }
                    }
                )
                
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.filter_rules_placeholder)) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, null)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    shape = CircleShape
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add_rule))
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredRules) { item ->
                RuleItem(
                    rule = item.rule,
                    isOverwrite = item.isOverwrite,
                    onEdit = { showEditDialog = item.rule },
                    onDelete = { if (item.isOverwrite) deleteRule(item.rule) }
                )
            }
        }
    }

    if (showDialog) {
        RuleDialog(
            onDismiss = { showDialog = false },
            onSave = { rule ->
                saveRule(rule)
                showDialog = false
            }
        )
    }

    showEditDialog?.let { rule ->
        RuleDialog(
            initialRule = rule,
            onDismiss = { showEditDialog = null },
            onSave = { newRule ->
                saveRule(newRule, rule)
                showEditDialog = null
            }
        )
    }

    if (showSyncDialog) {
        RemoteSyncDialog(
            url = updateUrl,
            onDismiss = { showSyncDialog = false },
            onSave = { newUrl ->
                scope.launch {
                    repository.setUpdateUrl(newUrl)
                    showSyncDialog = false
                    fetchRules()
                }
            }
        )
    }
}

@Composable
fun RemoteSyncDialog(
    url: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var tempUrl by remember { mutableStateOf(url) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.remote_rules_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = tempUrl,
                    onValueChange = { tempUrl = it },
                    label = { Text(stringResource(R.string.label_update_url)) },
                    placeholder = { Text(stringResource(R.string.placeholder_update_url)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    text = stringResource(R.string.hint_spoofing_download),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(tempUrl) }) {
                Text(stringResource(R.string.action_sync_remote))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun RuleItem(
    rule: Rule,
    isOverwrite: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val containerColor = if (isOverwrite) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    OutlinedCard(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (isOverwrite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (isOverwrite) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            if (isOverwrite) stringResource(R.string.rule_source_local) else stringResource(R.string.rule_source_synced),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = rule.patterns?.firstOrNull() ?: stringResource(R.string.rule_no_pattern),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val patternSize = rule.patterns?.size ?: 0
                    if (patternSize > 1) {
                        Text(
                            " +${patternSize - 1}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
                
                Spacer(Modifier.height(4.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Shield, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(4.dp))
                    val sniText = when (rule.targetSni) {
                        null -> stringResource(R.string.rule_sni_original)
                        "" -> stringResource(R.string.rule_sni_strip)
                        else -> rule.targetSni
                    }
                    Text(stringResource(R.string.rule_label_sni_format, sniText), style = MaterialTheme.typography.bodySmall)
                }
                if (!rule.targetIp.isNullOrEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.rule_label_ip_format, rule.targetIp), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (!rule.certVerify.isNullOrEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.rule_label_verify_format, rule.certVerify), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            if (isOverwrite) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                }
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack, 
                    contentDescription = null, 
                    modifier = Modifier.size(16.dp).rotate(180f),
                    tint = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}

@Composable
fun RuleDialog(
    initialRule: Rule? = null,
    onDismiss: () -> Unit,
    onSave: (Rule) -> Unit
) {
    var patterns by remember { mutableStateOf(initialRule?.patterns?.joinToString("\n") ?: "") }
    var sni by remember { mutableStateOf(initialRule?.targetSni ?: "") }
    var ip by remember { mutableStateOf(initialRule?.targetIp ?: "") }
    var verify by remember { mutableStateOf(initialRule?.certVerify ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialRule == null) stringResource(R.string.rule_dialog_title_add) else stringResource(R.string.rule_dialog_title_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = patterns,
                    onValueChange = { patterns = it },
                    label = { Text(stringResource(R.string.rule_label_patterns)) },
                    placeholder = { Text(stringResource(R.string.rule_placeholder_patterns)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5
                )
                OutlinedTextField(
                    value = sni,
                    onValueChange = { sni = it },
                    label = { Text(stringResource(R.string.rule_label_sni)) },
                    placeholder = { Text(stringResource(R.string.rule_placeholder_sni)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text(stringResource(R.string.rule_label_ip)) },
                    placeholder = { Text(stringResource(R.string.rule_placeholder_ip)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = verify,
                    onValueChange = { verify = it },
                    label = { Text(stringResource(R.string.rule_label_verify)) },
                    placeholder = { Text(stringResource(R.string.rule_placeholder_verify)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val patternList = patterns.split("\n")
                    .map { it.trim().trim('"', '\'').trimStart('#', '$') }
                    .filter { it.isNotEmpty() }
                onSave(Rule(patternList, sni.trim(), ip.trim().ifEmpty { null }, verify.trim().ifEmpty { null }))
            }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
