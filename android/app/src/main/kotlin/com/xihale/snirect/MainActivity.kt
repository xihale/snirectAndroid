package com.xihale.snirect

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.ui.res.stringResource
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.xihale.snirect.R
import com.xihale.snirect.data.model.LogEntry
import com.xihale.snirect.data.model.LogLevel
import com.xihale.snirect.data.repository.ConfigRepository
import com.xihale.snirect.service.SnirectVpnService
import com.xihale.snirect.service.VpnStatusManager
import com.xihale.snirect.ui.screens.*
import com.xihale.snirect.ui.theme.AppIcons
import com.xihale.snirect.ui.theme.SnirectTheme
import com.xihale.snirect.util.AppLogger
import com.xihale.snirect.util.CertUtil
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
    var statusText by mutableStateOf("DISCONNECTED")
    var uploadSpeed by mutableStateOf(0L)
    var downloadSpeed by mutableStateOf(0L)

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
            kotlinx.coroutines.MainScope().launch {
                val skipCheck = repository.skipCertCheck.first()
                if (!skipCheck && !CertUtil.isCaCertInstalled()) {
                    Toast.makeText(context, context.getString(R.string.toast_cert_install_required), Toast.LENGTH_LONG).show()
                    return@launch
                }
                prepareVpn(context)
            }
        }
    }

    private fun isCaCertInstalled(): Boolean {
        return CertUtil.isCaCertInstalled()
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
        VpnStatusManager.updateStatus(false, "STARTING")
    }

    private fun stopVpn(context: android.content.Context) {
        val intent = Intent(context, SnirectVpnService::class.java).apply {
            action = SnirectVpnService.ACTION_STOP
        }
        context.startService(intent)
        VpnStatusManager.updateStatus(false, "STOPPING")
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
                Toast.makeText(context, context.getString(R.string.toast_saved_to_downloads), Toast.LENGTH_LONG).show()
                
                val intent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
                context.startActivity(intent)
            } ?: throw Exception("Failed to create MediaStore entry")
        } catch (e: Exception) {
            AppLogger.e("CA export failed", e)
            Toast.makeText(context, context.getString(R.string.toast_export_failed, e.message), Toast.LENGTH_SHORT).show()
        }
    }
}

class MainActivity : AppCompatActivity() {
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

        kotlinx.coroutines.MainScope().launch {
            repository.language.collect { lang ->
                val appLocale: LocaleListCompat = if (lang == ConfigRepository.LANGUAGE_SYSTEM) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(lang)
                }
                AppCompatDelegate.setApplicationLocales(appLocale)
            }
        }
        
        setContent {
            SnirectTheme {
                val viewModel: MainViewModel = viewModel(factory = factory)
                val context = LocalContext.current
                
                LaunchedEffect(Unit) {
                    AppLogger.i("Auto-activation: Checking VPN status...")
                    val shouldActivate = repository.activateOnStartup.first()
                    if (shouldActivate && !viewModel.isRunning) {
                        val hasNotifPermission = if (android.os.Build.VERSION.SDK_INT >= 33) {
                            androidx.core.content.ContextCompat.checkSelfPermission(
                                context, android.Manifest.permission.POST_NOTIFICATIONS
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        } else true

                        if (hasNotifPermission) {
                            AppLogger.i("Auto-activation: Permission granted, triggering VPN...")
                            val skipCheck = repository.skipCertCheck.first()
                            if (!skipCheck && !CertUtil.isCaCertInstalled()) {
                                AppLogger.w("Auto-activation: Blocked - CA certificate not installed")
                                Toast.makeText(context, context.getString(R.string.toast_auto_start_blocked), Toast.LENGTH_LONG).show()
                            } else {
                                viewModel.startVpn(context)
                            }
                        } else {
                            AppLogger.i("Auto-activation: Notification permission not granted, waiting for user.")
                        }
                    } else {
                        AppLogger.i("Auto-activation: Skipped (shouldActivate=$shouldActivate, isRunning=${viewModel.isRunning})")
                    }
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
                            SnirectApp(navController = navController, viewModel = viewModel, repository = repository)
                        }
                        composable("help") {
                            HelpScreen(navController = navController)
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
    viewModel: MainViewModel,
    repository: ConfigRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val statusColor = if (viewModel.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val activeColor = if (viewModel.isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    var showCertPrompt by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val skipCheck = repository.skipCertCheck.first()
        if (!skipCheck && !CertUtil.isCaCertInstalled()) {
            showCertPrompt = true
        }
    }

    if (showCertPrompt) {
        AlertDialog(
            onDismissRequest = { showCertPrompt = false },
            title = { Text(stringResource(R.string.cert_required_title)) },
            text = { Text(stringResource(R.string.cert_required_msg)) },
            confirmButton = {
                Button(onClick = {
                    showCertPrompt = false
                    navController.navigate("help")
                }) { Text(stringResource(R.string.action_view_guide)) }
            },
            dismissButton = {
                TextButton(onClick = { showCertPrompt = false }) { Text(stringResource(R.string.action_later)) }
            }
        )
    }

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch {
                val skipCheck = repository.skipCertCheck.first()
                if (skipCheck || CertUtil.isCaCertInstalled()) {
                    viewModel.startVpn(context)
                } else {
                    AppLogger.w("VPN Launcher: Blocked - CA certificate not installed")
                    Toast.makeText(context, context.getString(R.string.toast_cert_install_required), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            scope.launch {
                val skipCheck = repository.skipCertCheck.first()
                if (skipCheck || CertUtil.isCaCertInstalled()) {
                    viewModel.startVpn(context)
                } else {
                    AppLogger.w("Notif Launcher: Blocked - CA certificate not installed")
                    Toast.makeText(context, context.getString(R.string.toast_cert_install_required), Toast.LENGTH_LONG).show()
                }
            }
        } else {
            Toast.makeText(context, context.getString(R.string.toast_notif_permission_required), Toast.LENGTH_SHORT).show()
        }
    }
    
    LaunchedEffect(Unit) {
        viewModel.vpnPermissionLauncher = vpnLauncher
        viewModel.notificationPermissionLauncher = notifLauncher
        
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                AppLogger.i("Startup: Requesting notification permission")
                notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        val vpnIntent = android.net.VpnService.prepare(context)
        if (vpnIntent != null) {
            AppLogger.i("Startup: Requesting VPN permission")
            vpnLauncher.launch(vpnIntent)
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { 
                    Column {
                        Text(stringResource(R.string.app_logo), fontWeight = FontWeight.Black)
                        Text(
                            text = getLocalizedStatus(viewModel.statusText),
                            style = MaterialTheme.typography.labelMedium,
                            color = statusColor
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("help") }) {
                        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.help_title))
                    }
                    IconButton(onClick = { navController.navigate("logs") }) {
                        Icon(AppIcons.Terminal, contentDescription = stringResource(R.string.logs_title))
                    }
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title))
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
                        colors = ButtonDefaults.buttonColors(containerColor = activeColor)
                    ) {
                        Text(if (viewModel.isRunning) stringResource(R.string.action_deactivate) else stringResource(R.string.action_activate))
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SpeedCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.label_upload),
                    speed = formatSpeed(viewModel.uploadSpeed),
                    icon = AppIcons.Speed,
                    color = MaterialTheme.colorScheme.primary
                )
                SpeedCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.label_download),
                    speed = formatSpeed(viewModel.downloadSpeed),
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
                    headlineContent = { Text(stringResource(R.string.https_decryption)) },
                    supportingContent = { Text(stringResource(R.string.https_decryption_desc)) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) }
                )
            }
            
            Text(
                stringResource(R.string.version_format, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun formatSpeed(bytes: Long): String {
    return when {
        bytes < 1024 -> stringResource(R.string.speed_format, bytes.toString())
        bytes < 1024 * 1024 -> stringResource(R.string.speed_kb_format, bytes / 1024.0)
        else -> stringResource(R.string.speed_mb_format, bytes / (1024.0 * 1024.0))
    }
}

@Composable
fun getLocalizedStatus(status: String): String {
    return when (status) {
        "DISCONNECTED" -> stringResource(R.string.vpn_status_disconnected)
        "STARTING" -> stringResource(R.string.vpn_status_starting)
        "STOPPING" -> stringResource(R.string.vpn_status_stopping)
        "ACTIVE" -> stringResource(R.string.vpn_status_active)
        "IDLE" -> stringResource(R.string.vpn_status_idle)
        else -> if (status.startsWith("FAILED:")) {
            val error = status.removePrefix("FAILED:")
            stringResource(R.string.vpn_status_connected_failed, error)
        } else {
            status
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
