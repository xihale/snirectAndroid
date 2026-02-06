package com.xihale.snirect.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.xihale.snirect.data.repository.ConfigRepository
import kotlinx.coroutines.launch

import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.NetworkCheck

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    repository: ConfigRepository
) {
    var dns by remember { mutableStateOf("") }
    var updateUrl by remember { mutableStateOf("") }
    var mtu by remember { mutableStateOf("1500") }
    var enableIpv6 by remember { mutableStateOf(false) }
    var checkHostname by remember { mutableStateOf(false) }
    var logLevel by remember { mutableStateOf("info") }
    
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        repository.dnsServer.collect { dns = it }
    }
    LaunchedEffect(Unit) {
        repository.updateUrl.collect { updateUrl = it }
    }
    LaunchedEffect(Unit) {
        repository.mtu.collect { mtu = it.toString() }
    }
    LaunchedEffect(Unit) {
        repository.enableIpv6.collect { enableIpv6 = it }
    }
    LaunchedEffect(Unit) {
        repository.checkHostname.collect { checkHostname = it }
    }
    LaunchedEffect(Unit) {
        repository.logLevel.collect { logLevel = it }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsGroup(title = "Network") {
                SettingsTile(
                    icon = Icons.Default.NetworkCheck,
                    title = "MTU",
                    subtitle = "Current: $mtu",
                    onClick = { }
                ) {
                    OutlinedTextField(
                        value = mtu,
                        onValueChange = { 
                            mtu = it
                            it.toIntOrNull()?.let { value -> scope.launch { repository.setMtu(value) } }
                        },
                        modifier = Modifier.width(100.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                }

                var showWarningDialog by remember { mutableStateOf(false) }
                SettingsTile(
                    icon = Icons.Default.BugReport,
                    title = "Check Hostname",
                    subtitle = "Verify SSL certificates",
                    onClick = { }
                ) {
                    Switch(
                        checked = checkHostname,
                        onCheckedChange = { 
                            if (!it) showWarningDialog = true 
                            else {
                                checkHostname = it
                                scope.launch { repository.setCheckHostname(it) }
                            }
                        }
                    )
                }

                if (showWarningDialog) {
                    AlertDialog(
                        onDismissRequest = { showWarningDialog = false },
                        title = { Text("Security Warning") },
                        text = { Text("Disabling hostname verification makes the connection vulnerable to MITM attacks.") },
                        confirmButton = {
                            TextButton(onClick = {
                                checkHostname = false
                                scope.launch { repository.setCheckHostname(false) }
                                showWarningDialog = false
                            }) { Text("Disable Anyway", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showWarningDialog = false }) { Text("Cancel") }
                        }
                    )
                }

                SettingsTile(
                    icon = Icons.Default.BugReport,
                    title = "IPv6 Support",
                    subtitle = "Enable IPv6 routing",
                    onClick = { }
                ) {
                    Switch(
                        checked = enableIpv6,
                        onCheckedChange = { 
                            enableIpv6 = it
                            scope.launch { repository.setEnableIpv6(it) }
                        }
                    )
                }
            }

            SettingsGroup(title = "Rules & DNS") {
                SettingsTile(
                    icon = Icons.Default.Dns,
                    title = "DNS Configuration",
                    subtitle = "DoH and Bootstrap DNS",
                    onClick = { navController.navigate("dns") }
                )
                
                SettingsTile(
                    icon = Icons.AutoMirrored.Filled.Rule,
                    title = "Traffic Rules",
                    subtitle = "Manage domain and IP rules",
                    onClick = { navController.navigate("rules") }
                )

                SettingsTile(
                    icon = Icons.Default.Update,
                    title = "Update URL",
                    subtitle = updateUrl,
                    onClick = { }
                )
            }

            SettingsGroup(title = "Logging & Debug") {
                var showLogDropdown by remember { mutableStateOf(false) }
                SettingsTile(
                    icon = Icons.Default.BugReport,
                    title = "Log Level",
                    subtitle = "Current: ${logLevel.uppercase()}",
                    onClick = { showLogDropdown = true }
                ) {
                    DropdownMenu(
                        expanded = showLogDropdown,
                        onDismissRequest = { showLogDropdown = false }
                    ) {
                        listOf("debug", "info", "warn", "error").forEach { level ->
                            DropdownMenuItem(
                                text = { Text(level.uppercase()) },
                                onClick = {
                                    logLevel = level
                                    scope.launch { repository.setLogLevel(level) }
                                    showLogDropdown = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun SettingsTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    ListItem(
        modifier = Modifier.clickable { onClick() },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = trailing
    )
}
