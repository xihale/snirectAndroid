package com.xihale.snirect.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.xihale.snirect.MainActivity
import com.xihale.snirect.data.model.LogEntry
import com.xihale.snirect.data.model.LogLevel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(navController: NavController) {
    val context = LocalContext.current
    val logs = MainActivity.logBuffer
    val listState = rememberLazyListState()
    
    var searchQuery by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) }
    var showFilterMenu by remember { mutableStateOf(false) }

    val filteredLogs = remember(logs.size, searchQuery, selectedLevel) {
        logs.filter { entry ->
            (selectedLevel == null || entry.level == selectedLevel) &&
            (searchQuery.isEmpty() || entry.message.contains(searchQuery, ignoreCase = true))
        }
    }

    val dateFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Log Viewer") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val logText = filteredLogs.joinToString("\n") { 
                                "[${dateFormat.format(Date(it.timestamp))}] [${it.level}] ${it.message}" 
                            }
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, logText)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "Share Logs"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                        IconButton(onClick = { logs.clear() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Logs")
                        }
                    }
                )
                
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = { },
                    active = false,
                    onActiveChange = { },
                    placeholder = { Text("Search logs...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) { }

                ScrollableTabRow(
                    selectedTabIndex = if (selectedLevel == null) 0 else selectedLevel!!.ordinal + 1,
                    edgePadding = 16.dp,
                    divider = {},
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[if (selectedLevel == null) 0 else selectedLevel!!.ordinal + 1])
                        )
                    }
                ) {
                    Tab(
                        selected = selectedLevel == null,
                        onClick = { selectedLevel = null },
                        text = { Text("ALL") }
                    )
                    LogLevel.values().forEach { level ->
                        Tab(
                            selected = selectedLevel == level,
                            onClick = { selectedLevel = level },
                            text = { Text(level.name) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No logs found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredLogs) { entry ->
                    LogItem(entry, dateFormat)
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun LogItem(entry: LogEntry, dateFormat: SimpleDateFormat) {
    val color = when (entry.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.WARN -> Color(0xFFFFA000)
        LogLevel.DEBUG -> Color(0xFF757575)
        else -> MaterialTheme.colorScheme.primary
    }

    ListItem(
        overlineContent = {
            Text(
                text = "${dateFormat.format(Date(entry.timestamp))} | ${entry.level}",
                color = color.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall
            )
        },
        headlineContent = {
            Text(
                text = entry.message,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = if (entry.level == LogLevel.DEBUG) color else MaterialTheme.colorScheme.onSurface
            )
        },
        leadingContent = {
            val icon = when (entry.level) {
                LogLevel.ERROR -> Icons.Default.Error
                LogLevel.WARN -> Icons.Default.Warning
                LogLevel.DEBUG -> Icons.Default.BugReport
                else -> Icons.Default.Info
            }
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
    )
}
