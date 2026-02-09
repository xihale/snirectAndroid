package com.xihale.snirect.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.xihale.snirect.data.repository.ConfigRepository
import kotlinx.coroutines.launch

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Shield

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsScreen(
    navController: NavController,
    repository: ConfigRepository
) {
    var nameservers by remember { mutableStateOf<List<String>>(emptyList()) }
    var bootstrapDns by remember { mutableStateOf<List<String>>(emptyList()) }
    
    val scope = rememberCoroutineScope()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var newNameserver by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        repository.nameservers.collect { nameservers = it }
    }
    LaunchedEffect(Unit) {
        repository.bootstrapDns.collect { bootstrapDns = it }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("DNS Resolver") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Server")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsGroup(title = "Bootstrap") {
                ListItem(
                    headlineContent = { Text("Bootstrap DNS") },
                    supportingContent = {
                        OutlinedTextField(
                            value = bootstrapDns.joinToString(","),
                            onValueChange = { 
                                val list = it.split(",").filter { s -> s.isNotBlank() }
                                scope.launch { repository.setBootstrapDns(list) }
                            },
                            placeholder = { Text("tls://223.5.5.5") },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium
                        )
                    },
                    leadingContent = { Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.primary) }
                )
            }

            SettingsGroup(title = "Upstream Nameservers (DoH/DoT)") {
                if (nameservers.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No custom nameservers", color = MaterialTheme.colorScheme.outline)
                    }
                }

                nameservers.forEach { server ->
                    ListItem(
                        headlineContent = { Text(server) },
                        leadingContent = { Icon(Icons.Default.Dns, null, tint = MaterialTheme.colorScheme.secondary) },
                        trailingContent = {
                            IconButton(onClick = {
                                val newList = nameservers.toMutableList()
                                newList.remove(server)
                                scope.launch { repository.setNameservers(newList) }
                            }) {
                                Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Upstream") },
            text = {
                OutlinedTextField(
                    value = newNameserver,
                    onValueChange = { newNameserver = it },
                    label = { Text("Endpoint URL") },
                    placeholder = { Text("https://dns.google/dns-query") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newNameserver.isNotBlank()) {
                        val newList = nameservers.toMutableList()
                        newList.add(newNameserver.trim())
                        scope.launch { repository.setNameservers(newList) }
                        newNameserver = ""
                        showAddDialog = false
                    }
                }) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
