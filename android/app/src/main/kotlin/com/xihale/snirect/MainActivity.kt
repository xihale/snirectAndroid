package com.xihale.snirect

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.xihale.snirect.data.model.LogEntry
import com.xihale.snirect.data.model.LogLevel
import com.xihale.snirect.data.repository.ConfigRepository
import com.xihale.snirect.service.SnirectVpnService
import com.xihale.snirect.service.VpnStatusManager
import com.xihale.snirect.ui.screens.*
import com.xihale.snirect.ui.theme.AppIcons
import com.xihale.snirect.ui.theme.SnirectTheme
import com.xihale.snirect.util.AppLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
        if (com.xihale.snirect.service.SnirectVpnService.isServiceRunning) {
            com.xihale.snirect.service.VpnStatusManager.updateStatus(true, "ACTIVE")
        }
        
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
            AppLogger.i("Starting CA certificate export using MediaStore...")
            val certBytes = core.Core.getCACertificate() ?: throw Exception("Go core returned null cert")
            if (certBytes.isEmpty()) throw Exception("Go core returned empty cert")
            
            val resolver = context.contentResolver
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "snirect_ca.crt")
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/x-x509-ca-cert")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }

            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(certBytes)
                }
                AppLogger.i("CA cert saved to Downloads via MediaStore: $uri")
                Toast.makeText(context, "Saved to Downloads/snirect_ca.crt", Toast.LENGTH_LONG).show()
                
                val intent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
                context.startActivity(intent)
            } ?: throw Exception("Failed to create MediaStore entry")
        } catch (e: Exception) {
            AppLogger.e("CA export failed", e)
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

class MainActivity : ComponentActivity() {
    companion object {
        val logBuffer = mutableStateListOf<LogEntry>()
        fun log(message: String) {
            val level = when {
                message.contains("[ERROR]", true) || message.contains("ERROR:", true) -> LogLevel.ERROR
                message.contains("[WARN]", true) || message.contains("WARN:", true) -> LogLevel.WARN
                message.contains("[DEBUG]", true) || message.contains("DEBUG:", true) -> LogLevel.DEBUG
                else -> LogLevel.INFO
            }
            val cleanMessage = message
                .replace("[ERROR]", "")
                .replace("[WARN]", "")
                .replace("[DEBUG]", "")
                .replace("[INFO]", "")
                .trim()

            Handler(Looper.getMainLooper()).post {
                if (logBuffer.size > 2000) logBuffer.removeAt(0)
                logBuffer.add(LogEntry(level = level, message = cleanMessage))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.i("App Starting...")
        
        core.Core.setDataDir(filesDir.absolutePath)
        
        val repository = ConfigRepository(applicationContext)
        val factory = MainViewModelFactory(repository)
        
        setContent {
            SnirectTheme {
                val viewModel: MainViewModel = viewModel(factory = factory)
                val context = LocalContext.current
                
                LaunchedEffect(Unit) {
                    AppLogger.i("Auto-activation: Checking VPN status...")
                    if (!viewModel.isRunning) {
                        val hasNotifPermission = if (android.os.Build.VERSION.SDK_INT >= 33) {
                            androidx.core.content.ContextCompat.checkSelfPermission(
                                context, android.Manifest.permission.POST_NOTIFICATIONS
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        } else true

                        if (hasNotifPermission) {
                            AppLogger.i("Auto-activation: Permission granted, triggering VPN...")
                            viewModel.startVpn(context)
                        } else {
                            AppLogger.i("Auto-activation: Notification permission not granted, waiting for user.")
                        }
                    }
                    
                    kotlinx.coroutines.delay(5000)
                    verifyPixiv()
                }

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
                            SnirectApp(navController = navController, viewModel = viewModel)
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

    private fun verifyPixiv() {
        AppLogger.i("Verification: Sending request to pixiv.net and google.com...")
        kotlinx.coroutines.MainScope().launch(kotlinx.coroutines.Dispatchers.IO) {
            val caBytes = core.Core.getCACertificate()
            
            val trustAllClient = okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .hostnameVerifier { _, _ -> true }
                .sslSocketFactory(createTrustAllSslSocketFactory(), createTrustAllManager())
                .dns(object : okhttp3.Dns {
                    override fun lookup(hostname: String): List<java.net.InetAddress> {
                        val all = okhttp3.Dns.SYSTEM.lookup(hostname)
                        val ipv4 = all.filter { it is java.net.Inet4Address }
                        return ipv4.ifEmpty { all }
                    }
                })
                .build()

            // Verify Pixiv
            try {
                val request = okhttp3.Request.Builder().url("https://www.pixiv.net").build()
                trustAllClient.newCall(request).execute().use { response ->
                    AppLogger.i("Verification SUCCESS: pixiv.net returned ${response.code}")
                }
            } catch (e: Exception) {
                AppLogger.e("Verification ERROR: pixiv.net failed", e)
            }

            // Verify Google (to trigger cert_verify)
            try {
                val request = okhttp3.Request.Builder().url("https://www.google.com").build()
                trustAllClient.newCall(request).execute().use { response ->
                    AppLogger.i("Verification SUCCESS: google.com returned ${response.code}")
                }
            } catch (e: Exception) {
                AppLogger.e("Verification ERROR: google.com failed", e)
            }
        }
    }

    private fun createTrustAllSslSocketFactory(): javax.net.ssl.SSLSocketFactory {
        val sc = javax.net.ssl.SSLContext.getInstance("SSL")
        sc.init(null, arrayOf(createTrustAllManager()), java.security.SecureRandom())
        return sc.socketFactory
    }

    private fun createTrustAllManager(): javax.net.ssl.X509TrustManager {
        return object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
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
    val statusColor = if (viewModel.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

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
            LargeTopAppBar(
                title = { 
                    Column {
                        Text("SNIRECT", fontWeight = FontWeight.Black)
                        Text(
                            text = if (viewModel.isRunning) "Service is active" else "Service is idle",
                            style = MaterialTheme.typography.labelMedium,
                            color = statusColor
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("logs") }) {
                        Icon(AppIcons.Terminal, contentDescription = "Logs")
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = if (viewModel.isRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { if (viewModel.isRunning) 1f else 0f },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 8.dp,
                            trackColor = MaterialTheme.colorScheme.outlineVariant,
                            color = statusColor,
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                        Icon(
                            imageVector = AppIcons.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = statusColor
                        )
                    }
                    
                    Spacer(Modifier.height(24.dp))
                    
                    Button(
                        onClick = { viewModel.toggleVpn(context) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = if (viewModel.isRunning) {
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        } else {
                            ButtonDefaults.buttonColors()
                        }
                    ) {
                        Text(if (viewModel.isRunning) "DEACTIVATE" else "ACTIVATE")
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SpeedCard(
                    modifier = Modifier.weight(1f),
                    title = "Upload",
                    speed = viewModel.uploadSpeed,
                    icon = AppIcons.Speed,
                    color = MaterialTheme.colorScheme.primary
                )
                SpeedCard(
                    modifier = Modifier.weight(1f),
                    title = "Download",
                    speed = viewModel.downloadSpeed,
                    icon = AppIcons.Speed,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.installCert(context) }
            ) {
                ListItem(
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                    headlineContent = { Text("HTTPS Decryption") },
                    supportingContent = { Text("Install CA Certificate to enable SNI modification for HTTPS") },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) }
                )
            }
            
            Text(
                "Version: ${BuildConfig.VERSION_NAME}\nCore: gVisor/2026.02",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun SpeedCard(
    modifier: Modifier = Modifier,
    title: String,
    speed: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    ElevatedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.labelMedium, color = color)
            }
            Spacer(Modifier.height(8.dp))
            Text(speed, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}
