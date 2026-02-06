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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    navController: NavController,
    repository: ConfigRepository
) {
    var rules by remember { mutableStateOf<List<Rule>>(emptyList()) }
    var showDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf<Rule?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var updateUrl by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        rules = repository.getRules()
        repository.updateUrl.collect { updateUrl = it }
    }

    fun saveRule(newRule: Rule, oldRule: Rule? = null) {
        val updatedList = rules.toMutableList()
        if (oldRule != null) {
            val index = updatedList.indexOf(oldRule)
            if (index != -1) updatedList[index] = newRule
        } else {
            updatedList.add(newRule)
        }
        rules = updatedList
        scope.launch { repository.saveRules(updatedList) }
    }

    fun deleteRule(rule: Rule) {
        val updatedList = rules.toMutableList()
        updatedList.remove(rule)
        rules = updatedList
        scope.launch { repository.saveRules(updatedList) }
    }

    fun fetchRules() {
        scope.launch {
            try {
                Toast.makeText(context, "Fetching rules...", Toast.LENGTH_SHORT).show()
                val newRules = repository.fetchRemoteRules(updateUrl)
                rules = newRules
                repository.saveRules(newRules)
                Toast.makeText(context, "Rules updated: ${newRules.size}", Toast.LENGTH_SHORT).show()
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
            items(rules) { rule ->
                RuleItem(
                    rule = rule,
                    onEdit = { showEditDialog = rule },
                    onDelete = { deleteRule(rule) }
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
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(onClick = onEdit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.patterns.joinToString(", "),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (rule.targetSni.isNotEmpty()) {
                    Text("SNI: ${rule.targetSni}", style = MaterialTheme.typography.bodyMedium)
                }
                if (rule.targetIp.isNotEmpty()) {
                    Text("IP: ${rule.targetIp}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
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
