package com.things5.realtimechat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.things5.realtimechat.data.ConnectionQuality
import com.things5.realtimechat.data.DebugLogEntry
import com.things5.realtimechat.data.DebugLogType
import com.things5.realtimechat.data.RealtimeDebugState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealtimeDebugScreen(
    debugState: RealtimeDebugState,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onClearLogs: () -> Unit
) {
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    
    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(debugState.logs.size) {
        if (debugState.logs.isNotEmpty()) {
            listState.animateScrollToItem(debugState.logs.size - 1)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug Realtime API") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Indietro")
                    }
                },
                actions = {
                    // Copy all logs
                    IconButton(onClick = {
                        val all = debugState.logs.joinToString(separator = "\n") { log ->
                            val ts = formatTimestamp(log.timestamp)
                            val details = log.details?.let { "\n  Details: $it" } ?: ""
                            "[$ts] ${log.type}: ${log.message}$details"
                        }
                        clipboard.setText(AnnotatedString(all))
                    }) {
                        Icon(Icons.Default.ContentCopy, "Copia tutti i log")
                    }
                    IconButton(onClick = onClearLogs) {
                        Icon(Icons.Default.Delete, "Cancella Log")
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, "Aggiorna")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Connection Status Card
            item {
                ConnectionStatusCard(debugState)
            }
            
            // Usage Statistics Card
            item {
                UsageStatisticsCard(debugState)
            }
            
            // Session Info Card
            item {
                SessionInfoCard(debugState)
            }
            
            // Logs Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Log Operazioni (${debugState.logs.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Logs
            if (debugState.logs.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Nessun log disponibile",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(debugState.logs) { log ->
                    LogEntryCard(log)
                }
            }
        }
    }
}

@Composable
fun ConnectionStatusCard(debugState: RealtimeDebugState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (debugState.isConnected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Stato Connessione",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Icon(
                    imageVector = if (debugState.isConnected) {
                        Icons.Default.CheckCircle
                    } else {
                        Icons.Default.Error
                    },
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = if (debugState.isConnected) {
                        Color(0xFF2E7D32)
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
            
            HorizontalDivider()
            
            // Connection Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Stato:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (debugState.isConnected) "Connesso" else "Disconnesso",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (debugState.isConnected) {
                        Color(0xFF2E7D32)
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
            
            // Connection Quality
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Qualità:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ConnectionQualityIndicator(debugState.connectionQuality)
                    Text(
                        text = when (debugState.connectionQuality) {
                            ConnectionQuality.EXCELLENT -> "Eccellente"
                            ConnectionQuality.GOOD -> "Buona"
                            ConnectionQuality.FAIR -> "Discreta"
                            ConnectionQuality.POOR -> "Scarsa"
                            ConnectionQuality.UNKNOWN -> "Sconosciuta"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            // Usage Limit Warning
            if (debugState.usageLimitReached) {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Limite di utilizzo raggiunto",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Last Error
            if (debugState.lastError != null) {
                HorizontalDivider()
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Ultimo Errore:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = debugState.lastError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun UsageStatisticsCard(debugState: RealtimeDebugState) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Statistiche Utilizzo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            HorizontalDivider()
            
            // Statistics Grid
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatisticRow(
                    icon = Icons.Default.Send,
                    label = "Chunk Audio Inviati",
                    value = debugState.audioChunksSent.toString(),
                    color = MaterialTheme.colorScheme.primary
                )
                
                StatisticRow(
                    icon = Icons.Default.Call,
                    label = "Chunk Audio Ricevuti",
                    value = debugState.audioChunksReceived.toString(),
                    color = MaterialTheme.colorScheme.secondary
                )
                
                StatisticRow(
                    icon = Icons.Default.Image,
                    label = "Immagini Processate",
                    value = debugState.imagesProcessed.toString(),
                    color = MaterialTheme.colorScheme.tertiary
                )
                
                StatisticRow(
                    icon = Icons.Default.Build,
                    label = "Tool Call Eseguiti",
                    value = debugState.toolCallsExecuted.toString(),
                    color = Color(0xFF9C27B0)
                )
                
                StatisticRow(
                    icon = Icons.Default.Error,
                    label = "Errori",
                    value = debugState.errors.toString(),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun SessionInfoCard(debugState: RealtimeDebugState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Informazioni Sessione",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            HorizontalDivider()
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Modello:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = debugState.model,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            
            if (debugState.sessionId != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Session ID:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = debugState.sessionId.take(16) + "...",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun LogEntryCard(log: DebugLogEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (log.type) {
                DebugLogType.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                DebugLogType.TOOL_CALL, DebugLogType.TOOL_RESPONSE -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon
            Icon(
                imageVector = when (log.type) {
                    DebugLogType.CONNECTION -> Icons.Default.Link
                    DebugLogType.AUDIO_SENT -> Icons.Default.Send
                    DebugLogType.AUDIO_RECEIVED -> Icons.Default.Call
                    DebugLogType.IMAGE_SENT -> Icons.Default.Image
                    DebugLogType.TOOL_CALL -> Icons.Default.Build
                    DebugLogType.TOOL_RESPONSE -> Icons.Default.CheckCircle
                    DebugLogType.MCP_CONNECTION -> Icons.Default.Link
                    DebugLogType.MCP_ERROR -> Icons.Default.Error
                    DebugLogType.MCP_TOOL_REGISTERED -> Icons.Default.CheckCircle
                    DebugLogType.ERROR -> Icons.Default.Error
                    DebugLogType.INFO -> Icons.Default.Info
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = when (log.type) {
                    DebugLogType.ERROR, DebugLogType.MCP_ERROR -> MaterialTheme.colorScheme.error
                    DebugLogType.TOOL_CALL -> Color(0xFF9C27B0)
                    DebugLogType.TOOL_RESPONSE -> Color(0xFF2E7D32)
                    DebugLogType.MCP_TOOL_REGISTERED -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            
            // Content
            val clipboard = LocalClipboardManager.current
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = log.message,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (log.type == DebugLogType.ERROR) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatTimestamp(log.timestamp),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = {
                            val details = log.details?.let { "\nDetails: $it" } ?: ""
                            val copyText = "[${formatTimestamp(log.timestamp)}] ${log.type}: ${log.message}$details"
                            clipboard.setText(AnnotatedString(copyText))
                        }) {
                            Icon(Icons.Default.ContentCopy, "Copia log")
                        }
                    }
                }
                
                if (log.details != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        SelectionContainer {
                            Text(
                                text = log.details,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatisticRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        Surface(
            color = color.copy(alpha = 0.2f),
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                color = color
            )
        }
    }
}

@Composable
fun ConnectionQualityIndicator(quality: ConnectionQuality) {
    val color = when (quality) {
        ConnectionQuality.EXCELLENT -> Color(0xFF2E7D32)
        ConnectionQuality.GOOD -> Color(0xFF66BB6A)
        ConnectionQuality.FAIR -> Color(0xFFFFA726)
        ConnectionQuality.POOR -> Color(0xFFEF5350)
        ConnectionQuality.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(((index + 1) * 4).dp)
                    .background(
                        color = if (index < when (quality) {
                            ConnectionQuality.EXCELLENT -> 4
                            ConnectionQuality.GOOD -> 3
                            ConnectionQuality.FAIR -> 2
                            ConnectionQuality.POOR -> 1
                            ConnectionQuality.UNKNOWN -> 0
                        }) color else color.copy(alpha = 0.3f),
                        shape = MaterialTheme.shapes.extraSmall
                    )
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
