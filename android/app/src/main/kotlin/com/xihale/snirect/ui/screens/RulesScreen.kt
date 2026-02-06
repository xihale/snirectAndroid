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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.xihale.snirect.data.model.Rule
import com.xihale.snirect.data.repository.ConfigRepository
import com.xihale.snirect.data.repository.RuleWithSource
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    navController: NavController,
    repository: ConfigRepository
) {
    var rulesWithSource by remember { mutableStateOf<List<RuleWithSource>>(emptyList()) }
    var showDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf<Rule?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var updateUrl by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        rulesWithSource = repository.getAllRulesWithSource()
        repository.updateUrl.collect { updateUrl = it }
    }

    fun saveRule(newRule: Rule, oldRule: Rule? = null) {
        scope.launch {
            val currentLocalRules = repository.getAllRulesWithSource()
                .filter { it.isOverwrite }
                .map { it.rule }
                .toMutableList()
            
            if (oldRule != null) {
                val index = currentLocalRules.indexOf(oldRule)
                if (index != -1) currentLocalRules[index] = newRule
            } else {
                currentLocalRules.add(newRule)
            }
            repository.saveLocalRules(currentLocalRules)
            rulesWithSource = repository.getAllRulesWithSource()
        }
    }

    fun deleteRule(rule: Rule) {
        scope.launch {
            val currentLocalRules = repository.getAllRulesWithSource()
                .filter { it.isOverwrite }
                .map { it.rule }
                .toMutableList()
            currentLocalRules.remove(rule)
            repository.saveLocalRules(currentLocalRules)
            rulesWithSource = repository.getAllRulesWithSource()
        }
    }

    fun fetchRules() {
        scope.launch {
            try {
                Toast.makeText(context, "Fetching rules...", Toast.LENGTH_SHORT).show()
                repository.fetchRemoteRules(updateUrl)
                rulesWithSource = repository.getAllRulesWithSource()
                Toast.makeText(context, "Rules updated", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rules Management") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { fetchRules() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Update Rules")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Rule")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(rulesWithSource) { item ->
                RuleItem(
                    rule = item.rule,
                    isOverwrite = item.isOverwrite,
                    onEdit = { if (item.isOverwrite) showEditDialog = item.rule },
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
}

@Composable
fun RuleItem(
    rule: Rule,
    isOverwrite: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val cardColor = if (isOverwrite) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    ElevatedCard(
        onClick = onEdit,
        colors = CardDefaults.elevatedCardColors(containerColor = cardColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isOverwrite) "[Overwrite]" else "[Download]",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOverwrite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = rule.patterns.joinToString(", "),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (rule.targetSni.isNotEmpty()) {
                    Text("SNI: ${rule.targetSni}", style = MaterialTheme.typography.bodyMedium)
                }
                if (rule.targetIp.isNotEmpty()) {
                    Text("IP: ${rule.targetIp}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (isOverwrite) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialRule == null) "Add Rule" else "Edit Rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = patterns,
                    onValueChange = { patterns = it },
                    label = { Text("Patterns (one per line)") },
                    minLines = 3,
                    maxLines = 5
                )
                OutlinedTextField(
                    value = sni,
                    onValueChange = { sni = it },
                    label = { Text("Target SNI (Optional)") }
                )
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("Target IP (Optional)") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val patternList = patterns.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                onSave(Rule(patternList, sni, ip))
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
