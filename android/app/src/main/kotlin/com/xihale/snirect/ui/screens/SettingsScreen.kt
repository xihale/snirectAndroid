package com.xihale.snirect.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        repository.dnsServer.collect { dns = it }
    }
    LaunchedEffect(Unit) {
        repository.updateUrl.collect { updateUrl = it }
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = dns,
                onValueChange = { 
                    dns = it
                    scope.launch { repository.setDnsServer(it) }
                },
                label = { Text("DNS Server") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = updateUrl,
                onValueChange = { 
                    updateUrl = it
                    scope.launch { repository.setUpdateUrl(it) }
                },
                label = { Text("Rules Update URL") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { navController.navigate("rules") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Manage Rules")
            }
        }
    }
}
