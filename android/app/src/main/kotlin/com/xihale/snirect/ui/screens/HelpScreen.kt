package com.xihale.snirect.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(navController: NavController) {
    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { Text("Help & Guide") },
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
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            HelpSection(
                title = "1. Why Install CA Certificate?",
                content = "To support HTTPS decryption and SNI modification, Snirect needs a Root CA certificate installed in your system. This allows the app to handle encrypted traffic for domains like Pixiv."
            )

            HelpSection(
                title = "2. How to Install (Android 11+)",
                content = "1. Click 'HTTPS Decryption' on the home screen to export 'snirect_ca.crt' to your Downloads folder.\n" +
                        "2. Go to System Settings -> Security -> Encryption & credentials.\n" +
                        "3. Select 'Install a certificate' -> 'CA certificate'.\n" +
                        "4. Tap 'Install anyway' on the warning.\n" +
                        "5. Pick the exported certificate file."
            )

            HelpSection(
                title = "3. Starting the Service",
                content = "Click 'ACTIVATE' on the main screen. Grant VPN permission if prompted. Once active, your traffic will be routed through the Snirect core for SNI modification."
            )

            HelpSection(
                title = "4. DNS & Rules",
                content = "You can configure custom DNS servers (DoH) and traffic rules in the Settings menu. Rules allow you to define which domains should have their SNI or IP modified."
            )

            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "Note: If you encounter certificate errors in apps, ensure the CA certificate is correctly installed in the 'User' trust store.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun HelpSection(title: String, content: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = content,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
