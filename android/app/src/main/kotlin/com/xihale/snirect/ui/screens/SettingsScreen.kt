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
            TopAppBar(
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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Network Settings",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = mtu,
                onValueChange = { 
                    mtu = it
                    it.toIntOrNull()?.let { value ->
                        scope.launch { repository.setMtu(value) }
                    }
                },
                label = { Text("MTU") },
                supportingText = { Text("Standard: 1500") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            var showWarningDialog by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Check Hostname", style = MaterialTheme.typography.bodyLarge)
                    Text("Verify SSL certificate of target server", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = checkHostname,
                    onCheckedChange = { 
                        if (!it) {
                            showWarningDialog = true
                        } else {
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
                    text = { Text("Disabling hostname verification makes the connection vulnerable to Man-In-The-Middle (MITM) attacks. Are you sure?") },
                    confirmButton = {
                        TextButton(onClick = {
                            checkHostname = false
                            scope.launch { repository.setCheckHostname(false) }
                            showWarningDialog = false
                        }) {
                            Text("Disable Anyway", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showWarningDialog = false }) {
                            Text("Keep Enabled")
                        }
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Enable IPv6", style = MaterialTheme.typography.bodyLarge)
                    Text("Experimental support", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = enableIpv6,
                    onCheckedChange = { 
                        enableIpv6 = it
                        scope.launch { repository.setEnableIpv6(it) }
                    }
                )
            }

            OutlinedCard(
                onClick = { navController.navigate("dns") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "DNS Configuration",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Configure DoH and Bootstrap DNS",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Go"
                    )
                }
            }

            HorizontalDivider()

            Text(
                text = "Rules & Updates",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = updateUrl,
                onValueChange = { 
                    updateUrl = it
                    scope.launch { repository.setUpdateUrl(it) }
                },
                label = { Text("Rules Update URL") },
                supportingText = { Text("URL to fetch remote rules JSON") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedCard(
                onClick = { navController.navigate("rules") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Manage Rules",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Configure Priority 2 Overwrites",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Go"
                    )
                }
            }

            HorizontalDivider()

            Text(
                text = "Advanced",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            var showLogDropdown by remember { mutableStateOf(false) }
            Box {
                OutlinedTextField(
                    value = logLevel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Log Level") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { showLogDropdown = true }) {
                            Icon(androidx.compose.material.icons.Icons.Default.ArrowDropDown, "Select")
                        }
                    }
                )
                DropdownMenu(
                    expanded = showLogDropdown,
                    onDismissRequest = { showLogDropdown = false }
                ) {
                    listOf("debug", "info", "warn", "error").forEach { level ->
                        DropdownMenuItem(
                            text = { Text(level) },
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
