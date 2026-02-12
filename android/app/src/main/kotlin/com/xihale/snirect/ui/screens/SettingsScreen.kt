package com.xihale.snirect.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.xihale.snirect.data.repository.ConfigRepository
import com.xihale.snirect.ui.theme.AppIcons
import kotlinx.coroutines.launch

import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.res.stringResource
import com.xihale.snirect.R

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
    var activateOnStartup by remember { mutableStateOf(true) }
    var activateOnBoot by remember { mutableStateOf(false) }
    var skipCertCheck by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf(ConfigRepository.LANGUAGE_SYSTEM) }
    var filterMode by remember { mutableIntStateOf(ConfigRepository.FILTER_MODE_NONE) }
    var whitelistPackages by remember { mutableStateOf(setOf<String>()) }
    var bypassLan by remember { mutableStateOf(true) }
    var strictDoh by remember { mutableStateOf(false) }
    var blockIpv6 by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
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
    LaunchedEffect(Unit) {
        repository.activateOnStartup.collect { activateOnStartup = it }
    }
    LaunchedEffect(Unit) {
        repository.activateOnBoot.collect { activateOnBoot = it }
    }
    LaunchedEffect(Unit) {
        repository.skipCertCheck.collect { skipCertCheck = it }
    }
    LaunchedEffect(Unit) {
        repository.language.collect { language = it }
    }
    LaunchedEffect(Unit) {
        repository.filterMode.collect { filterMode = it }
    }
    LaunchedEffect(Unit) {
        repository.whitelistPackages.collect { whitelistPackages = it }
    }
    LaunchedEffect(Unit) {
        repository.bypassLan.collect { bypassLan = it }
    }
    LaunchedEffect(Unit) {
        repository.strictDoh.collect { strictDoh = it }
    }
    LaunchedEffect(Unit) {
        repository.blockIpv6.collect { blockIpv6 = it }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
            SettingsGroup(title = stringResource(R.string.group_general)) {
                var showLanguageDropdown by remember { mutableStateOf(false) }
                val languageLabel = when (language) {
                    ConfigRepository.LANGUAGE_SYSTEM -> stringResource(R.string.lang_system)
                    "en" -> stringResource(R.string.lang_en)
                    "zh" -> stringResource(R.string.lang_zh)
                    else -> language
                }

                SettingsTile(
                    icon = AppIcons.Language,
                    title = stringResource(R.string.setting_language),
                    subtitle = languageLabel,
                    onClick = { showLanguageDropdown = true }
                ) {
                    DropdownMenu(
                        expanded = showLanguageDropdown,
                        onDismissRequest = { showLanguageDropdown = false }
                    ) {
                        listOf(
                            ConfigRepository.LANGUAGE_SYSTEM to stringResource(R.string.lang_system),
                            "en" to stringResource(R.string.lang_en),
                            "zh" to stringResource(R.string.lang_zh)
                        ).forEach { (code, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    scope.launch { repository.setLanguage(code) }
                                    showLanguageDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            SettingsGroup(title = stringResource(R.string.group_network)) {
                var showFilterDropdown by remember { mutableStateOf(false) }
                val filterLabel = when (filterMode) {
                    ConfigRepository.FILTER_MODE_WHITELIST -> stringResource(R.string.setting_filter_mode_whitelist)
                    ConfigRepository.FILTER_MODE_BLACKLIST -> stringResource(R.string.setting_filter_mode_blacklist)
                    else -> stringResource(R.string.setting_filter_mode_none)
                }

                SettingsTile(
                    icon = AppIcons.Rule,
                    title = stringResource(R.string.setting_filter_mode),
                    subtitle = filterLabel,
                    onClick = { showFilterDropdown = true }
                ) {
                    DropdownMenu(
                        expanded = showFilterDropdown,
                        onDismissRequest = { showFilterDropdown = false }
                    ) {
                        listOf(
                            ConfigRepository.FILTER_MODE_NONE to stringResource(R.string.setting_filter_mode_none),
                            ConfigRepository.FILTER_MODE_WHITELIST to stringResource(R.string.setting_filter_mode_whitelist),
                            ConfigRepository.FILTER_MODE_BLACKLIST to stringResource(R.string.setting_filter_mode_blacklist)
                        ).forEach { (mode, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    scope.launch { repository.setFilterMode(mode) }
                                    showFilterDropdown = false
                                }
                            )
                        }
                    }
                }

                if (filterMode != ConfigRepository.FILTER_MODE_NONE) {
                    SettingsTile(
                        icon = Icons.Default.Edit,
                        title = stringResource(R.string.setting_whitelist_apps),
                        subtitle = stringResource(R.string.setting_whitelist_apps_count, whitelistPackages.size),
                        onClick = { navController.navigate("app_whitelist") }
                    )
                }

                SettingsTile(
                    icon = AppIcons.Speed,
                    title = stringResource(R.string.setting_bypass_lan),
                    subtitle = stringResource(R.string.setting_bypass_lan_desc),
                    onClick = null
                ) {
                    Switch(
                        checked = bypassLan,
                        onCheckedChange = {
                            bypassLan = it
                            scope.launch { repository.setBypassLan(it) }
                        }
                    )
                }

                var showWarningDialog by remember { mutableStateOf(false) }
                SettingsTile(
                    icon = AppIcons.BugReport,
                    title = stringResource(R.string.setting_check_hostname),
                    subtitle = stringResource(R.string.setting_check_hostname_desc),
                    onClick = null
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
                        title = { Text(stringResource(R.string.security_warning_title)) },
                        text = { Text(stringResource(R.string.security_warning_msg)) },
                        confirmButton = {
                            TextButton(onClick = {
                                checkHostname = false
                                scope.launch { repository.setCheckHostname(false) }
                                showWarningDialog = false
                            }) { Text(stringResource(R.string.action_disable_anyway), color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showWarningDialog = false }) { Text(stringResource(R.string.action_cancel)) }
                        }
                    )
                }

                SettingsTile(
                    icon = AppIcons.BugReport,
                    title = stringResource(R.string.setting_ipv6),
                    subtitle = stringResource(R.string.setting_ipv6_desc),
                    onClick = null
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

            SettingsGroup(title = stringResource(R.string.group_automation_security)) {
                SettingsTile(
                    icon = AppIcons.Terminal,
                    title = stringResource(R.string.setting_active_startup),
                    subtitle = stringResource(R.string.setting_active_startup_desc),
                    onClick = null
                ) {
                    Switch(
                        checked = activateOnStartup,
                        onCheckedChange = {
                            activateOnStartup = it
                            scope.launch { repository.setActivateOnStartup(it) }
                        }
                    )
                }

                SettingsTile(
                    icon = AppIcons.Update,
                    title = stringResource(R.string.setting_active_boot),
                    subtitle = stringResource(R.string.setting_active_boot_desc),
                    onClick = null
                ) {
                    Switch(
                        checked = activateOnBoot,
                        onCheckedChange = {
                            activateOnBoot = it
                            scope.launch { repository.setActivateOnBoot(it) }
                        }
                    )
                }

                SettingsTile(
                    icon = AppIcons.Shield,
                    title = stringResource(R.string.setting_skip_cert_check),
                    subtitle = stringResource(R.string.setting_skip_cert_check_desc),
                    onClick = null
                ) {
                    Switch(
                        checked = skipCertCheck,
                        onCheckedChange = {
                            skipCertCheck = it
                            scope.launch { repository.setSkipCertCheck(it) }
                        }
                    )
                }
            }

            SettingsGroup(title = stringResource(R.string.group_rules_dns)) {
                SettingsTile(
                    icon = AppIcons.Dns,
                    title = stringResource(R.string.setting_dns_config),
                    subtitle = stringResource(R.string.setting_dns_config_desc),
                    onClick = { navController.navigate("dns") }
                )
                
                SettingsTile(
                    icon = AppIcons.Rule,
                    title = stringResource(R.string.setting_traffic_rules),
                    subtitle = stringResource(R.string.setting_traffic_rules_desc),
                    onClick = { navController.navigate("rules") }
                )
            }

            SettingsGroup(title = stringResource(R.string.group_advanced)) {
                SettingsTile(
                    icon = AppIcons.Dns,
                    title = stringResource(R.string.setting_strict_doh),
                    subtitle = stringResource(R.string.setting_strict_doh_desc),
                    onClick = null
                ) {
                    Switch(
                        checked = strictDoh,
                        onCheckedChange = {
                            strictDoh = it
                            scope.launch { repository.setStrictDoh(it) }
                        }
                    )
                }

                SettingsTile(
                    icon = AppIcons.BugReport,
                    title = stringResource(R.string.setting_block_ipv6),
                    subtitle = stringResource(R.string.setting_block_ipv6_desc),
                    onClick = null
                ) {
                    Switch(
                        checked = blockIpv6,
                        onCheckedChange = {
                            blockIpv6 = it
                            scope.launch { repository.setBlockIpv6(it) }
                        }
                    )
                }

                var showMtuDialog by remember { mutableStateOf(false) }
                SettingsTile(
                    icon = AppIcons.NetworkCheck,
                    title = stringResource(R.string.setting_mtu),
                    subtitle = stringResource(R.string.setting_mtu_desc, mtu),
                    onClick = { showMtuDialog = true }
                )

                if (showMtuDialog) {
                    var tempMtu by remember { mutableStateOf(mtu) }
                    AlertDialog(
                        onDismissRequest = { showMtuDialog = false },
                        title = { Text(stringResource(R.string.setting_mtu)) },
                        text = {
                            OutlinedTextField(
                                value = tempMtu,
                                onValueChange = { if (it.all { char -> char.isDigit() }) tempMtu = it },
                                label = { Text("Bytes") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                )
                            )
                        },
                        confirmButton = {
                            Button(onClick = {
                                tempMtu.toIntOrNull()?.let { value ->
                                    mtu = value.toString()
                                    scope.launch { repository.setMtu(value) }
                                }
                                showMtuDialog = false
                            }) { Text(stringResource(R.string.action_save)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showMtuDialog = false }) { Text(stringResource(R.string.action_cancel)) }
                        }
                    )
                }

                var showLogDropdown by remember { mutableStateOf(false) }
                SettingsTile(
                    icon = AppIcons.BugReport,
                    title = stringResource(R.string.setting_log_level),
                    subtitle = stringResource(R.string.setting_log_level_desc, logLevel.uppercase()),
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
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    ListItem(
        modifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier,
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = trailing
    )
}