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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsScreen(
    navController: NavController,
    repository: ConfigRepository
) {
    var nameservers by remember { mutableStateOf<List<String>>(emptyList()) }
    var bootstrapDns by remember { mutableStateOf("") }
    var checkHostname by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var newNameserver by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        repository.nameservers.collect { nameservers = it }
    }
    LaunchedEffect(Unit) {
        repository.bootstrapDns.collect { bootstrapDns = it }
    }
    LaunchedEffect(Unit) {
        repository.checkHostname.collect { checkHostname = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DNS Configuration") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Resolver Settings",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = bootstrapDns,
                onValueChange = { 
                    bootstrapDns = it
                    scope.launch { repository.setBootstrapDns(it) }
                },
                label = { Text("Bootstrap DNS") },
                supportingText = { Text("IP address to resolve DoH domains (default: tls://223.5.5.5)") },
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Nameservers (DoH)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Server")
                }
            }

            nameservers.forEach { server ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = server,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            val newList = nameservers.toMutableList()
                            newList.remove(server)
                            scope.launch { repository.setNameservers(newList) }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove")
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Nameserver") },
            text = {
                OutlinedTextField(
                    value = newNameserver,
                    onValueChange = { newNameserver = it },
                    label = { Text("DoH URL") },
                    placeholder = { Text("https://dns.google/dns-query") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
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
