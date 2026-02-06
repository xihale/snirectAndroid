package com.xihale.snirect

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.xihale.snirect.data.repository.ConfigRepository
import com.xihale.snirect.service.SnirectVpnService
import com.xihale.snirect.service.VpnStatusManager
import com.xihale.snirect.ui.screens.*
import com.xihale.snirect.ui.theme.SnirectTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MainViewModelFactory(private val repository: ConfigRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class MainViewModel(private val repository: ConfigRepository) : ViewModel() {
    var isRunning by mutableStateOf(false)
    var statusText by mutableStateOf("已断开")
    var uploadSpeed by mutableStateOf("0 B/s")
    var downloadSpeed by mutableStateOf("0 B/s")

    var vpnPermissionLauncher: androidx.activity.result.ActivityResultLauncher<Intent>? = null
    var notificationPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>? = null

    init {
        kotlinx.coroutines.MainScope().launch {
            VpnStatusManager.isRunning.collect { isRunning = it }
        }
        kotlinx.coroutines.MainScope().launch {
            VpnStatusManager.statusText.collect { statusText = it }
        }
        kotlinx.coroutines.MainScope().launch {
            VpnStatusManager.uploadSpeed.collect { uploadSpeed = it }
        }
        kotlinx.coroutines.MainScope().launch {
            VpnStatusManager.downloadSpeed.collect { downloadSpeed = it }
        }
    }

    fun toggleVpn(context: android.content.Context) {
        if (isRunning) {
            stopVpn(context)
        } else {
            prepareVpn(context)
        }
    }

    private fun prepareVpn(context: android.content.Context) {
        val intent = VpnService.prepare(context)
        if (intent != null) {
            vpnPermissionLauncher?.launch(intent)
        } else {
            startVpn(context)
        }
    }

    fun startVpn(context: android.content.Context) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher?.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        
        val intent = Intent(context, SnirectVpnService::class.java).apply {
            action = SnirectVpnService.ACTION_START
        }
        context.startForegroundService(intent)
        VpnStatusManager.updateStatus(false, "启动中...")
    }

    private fun stopVpn(context: android.content.Context) {
        val intent = Intent(context, SnirectVpnService::class.java).apply {
            action = SnirectVpnService.ACTION_STOP
        }
        context.startService(intent)
        VpnStatusManager.updateStatus(false, "停止中...")
    }

    fun installCert(context: android.content.Context) {
        try {
            val certBytes = core.Core.getCACertificate() ?: throw Exception("Go core returned null cert")
            val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (!downloadDir.exists()) downloadDir.mkdirs()
            val file = java.io.File(downloadDir, "snirect_ca.crt")
            file.writeBytes(certBytes)
            Toast.makeText(context, "Saved to: Download/snirect_ca.crt", Toast.LENGTH_LONG).show()
            val intent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

class MainActivity : ComponentActivity() {
    companion object {
        val logBuffer = mutableStateListOf<String>()
        fun log(message: String) {
            if (logBuffer.size > 1000) logBuffer.removeAt(0)
            logBuffer.add(message)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = ConfigRepository(applicationContext)
        val factory = MainViewModelFactory(repository)
        
        setContent {
            SnirectTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
                        exitTransition = { fadeOut(animationSpec = tween(300)) },
                        popEnterTransition = { fadeIn(animationSpec = tween(300)) },
                        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
                    ) {
                        composable("home") {
                            SnirectApp(navController = navController, viewModel = viewModel(factory = factory))
                        }
                        composable("settings") {
                            SettingsScreen(navController = navController, repository = repository)
                        }
                        composable("dns") {
                            DnsScreen(navController = navController, repository = repository)
                        }
                        composable("rules") {
                            RulesScreen(navController = navController, repository = repository)
                        }
                        composable("logs") {
                            LogsScreen(navController = navController)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnirectApp(
    navController: androidx.navigation.NavController,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val statusColor = if (viewModel.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startVpn(context)
        }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startVpn(context)
        } else {
            Toast.makeText(context, "Notification permission required for VPN status", Toast.LENGTH_SHORT).show()
        }
    }
    
    LaunchedEffect(Unit) {
        viewModel.vpnPermissionLauncher = vpnLauncher
        viewModel.notificationPermissionLauncher = notifLauncher
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("SNIRECT", fontWeight = FontWeight.Black) },
                actions = {
                    IconButton(onClick = { navController.navigate("logs") }) {
                        Icon(Icons.Default.Terminal, contentDescription = "Logs")
                    }
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(240.dp).padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    color = statusColor.copy(alpha = 0.1f),
                    border = androidx.compose.foundation.BorderStroke(2.dp, statusColor)
                ) { }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = if (viewModel.isRunning) "ACTIVE" else "IDLE", color = statusColor)
                    Text(text = viewModel.uploadSpeed, style = MaterialTheme.typography.headlineMedium)
                    Text(text = viewModel.downloadSpeed, style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("VPN Service", style = MaterialTheme.typography.titleMedium)
                        Text(viewModel.statusText, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = viewModel.isRunning, onCheckedChange = { viewModel.toggleVpn(context) })
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { viewModel.installCert(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("INSTALL CA CERTIFICATE")
            }
            
            Spacer(modifier = Modifier.weight(1f))
            Text("Go Engine: gVisor/netstack active", style = MaterialTheme.typography.labelSmall)
        }
    }
}
