package com.xihale.snirect.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.xihale.snirect.R
import com.xihale.snirect.data.repository.ConfigRepository
import com.xihale.snirect.ui.theme.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class AppItem(
    val packageName: String,
    val label: String,
    val isSystem: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppWhitelistScreen(
    navController: NavController,
    repository: ConfigRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    var whitelistPackages by remember { mutableStateOf(setOf<String>()) }
    var allApps by remember { mutableStateOf(emptyList<AppItem>()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        repository.whitelistPackages.collect { whitelistPackages = it }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val mapped = apps.map { info ->
                AppItem(
                    packageName = info.packageName,
                    label = pm.getApplicationLabel(info).toString(),
                    isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }.sortedBy { it.label.lowercase() }
            
            withContext(Dispatchers.Main) {
                allApps = mapped
                isLoading = false
            }
        }
    }

    val filteredApps = remember(searchQuery, showSystemApps, allApps) {
        allApps.asSequence()
            .filter { if (!showSystemApps) !it.isSystem else true }
            .filter { 
                if (searchQuery.isBlank()) true 
                else it.label.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true)
            }
            .toList()
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = { Text(stringResource(R.string.setting_whitelist_apps)) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        FilterChip(
                            selected = showSystemApps,
                            onClick = { showSystemApps = !showSystemApps },
                            label = { Text("System") },
                            leadingIcon = if (showSystemApps) {
                                { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else null
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                )
                
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = {},
                    active = false,
                    onActiveChange = {},
                    placeholder = { Text(stringResource(R.string.search_logs_placeholder)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, null) } }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp),
                    colors = SearchBarDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    windowInsets = WindowInsets(0.dp)
                ) {}
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val isChecked = whitelistPackages.contains(app.packageName)
                    ListItem(
                        modifier = Modifier.clickable {
                            val newSet = if (isChecked) whitelistPackages - app.packageName else whitelistPackages + app.packageName
                            whitelistPackages = newSet
                            scope.launch { repository.setWhitelistPackages(newSet) }
                        },
                        headlineContent = { Text(app.label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                        supportingContent = { Text(app.packageName, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) },
                        leadingContent = {
                            Checkbox(checked = isChecked, onCheckedChange = null)
                        },
                        trailingContent = {
                            if (app.isSystem) {
                                Text("SYS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    )
                }
            }
        }
    }
}
