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
import com.xihale.snirect.ui.theme.AppIcons
import kotlinx.coroutines.launch

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.res.stringResource
import com.xihale.snirect.R

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
                title = { Text(stringResource(R.string.dns_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add_server))
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
            SettingsGroup(title = stringResource(R.string.group_bootstrap)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.label_bootstrap_dns)) },
                    supportingContent = {
                        OutlinedTextField(
                            value = bootstrapDns.joinToString(","),
                            onValueChange = { 
                                val list = it.split(",").filter { s -> s.isNotBlank() }
                                scope.launch { repository.setBootstrapDns(list) }
                            },
                            placeholder = { Text(stringResource(R.string.placeholder_bootstrap_dns)) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium
                        )
                    },
                    leadingContent = { Icon(AppIcons.Shield, null, tint = MaterialTheme.colorScheme.primary) }
                )
            }

            SettingsGroup(title = stringResource(R.string.group_upstream)) {
                if (nameservers.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_custom_nameservers), color = MaterialTheme.colorScheme.outline)
                    }
                }

                nameservers.forEach { server ->
                    ListItem(
                        headlineContent = { Text(server) },
                        leadingContent = { Icon(AppIcons.Dns, null, tint = MaterialTheme.colorScheme.secondary) },
                        trailingContent = {
                            IconButton(onClick = {
                                val newList = nameservers.toMutableList()
                                newList.remove(server)
                                scope.launch { repository.setNameservers(newList) }
                            }) {
                                Icon(Icons.Default.Delete, stringResource(R.string.action_remove), tint = MaterialTheme.colorScheme.error)
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
            title = { Text(stringResource(R.string.dialog_add_upstream)) },
            text = {
                OutlinedTextField(
                    value = newNameserver,
                    onValueChange = { newNameserver = it },
                    label = { Text(stringResource(R.string.label_endpoint_url)) },
                    placeholder = { Text(stringResource(R.string.placeholder_endpoint_url)) },
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
                    Text(stringResource(R.string.action_add))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
