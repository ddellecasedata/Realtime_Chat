package com.things5.realtimechat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.things5.realtimechat.viewmodel.McpToolsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpToolsScreen(
    viewModel: McpToolsViewModel,
    serverName: String,
    onNavigateBack: () -> Unit
) {
    val toolsState by viewModel.toolsState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var selectedToolForEdit by remember { mutableStateOf<McpToolsViewModel.ToolUiState?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Gestione Tool MCP")
                        Text(
                            text = serverName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Indietro")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (toolsState.isEmpty()) {
                // No tools available
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Build,
                        contentDescription = "Nessun tool",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Nessun tool disponibile",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Il server non ha ancora caricato i tool",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Tools list
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header stats
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Tool Totali",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "${toolsState.size}",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Attivi",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "${toolsState.count { it.enabled }}",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                    
                    // Tool items
                    items(toolsState) { tool ->
                        ToolCard(
                            tool = tool,
                            onToggle = { viewModel.toggleTool(tool.name) },
                            onEditDescription = { selectedToolForEdit = tool }
                        )
                    }
                }
            }
        }
    }
    
    // Edit description dialog
    selectedToolForEdit?.let { tool ->
        EditDescriptionDialog(
            tool = tool,
            onDismiss = { selectedToolForEdit = null },
            onSave = { newDescription ->
                viewModel.updateToolDescription(tool.name, newDescription)
                selectedToolForEdit = null
            },
            onReset = {
                viewModel.resetToolDescription(tool.name)
                selectedToolForEdit = null
            }
        )
    }
}

@Composable
private fun ToolCard(
    tool: McpToolsViewModel.ToolUiState,
    onToggle: () -> Unit,
    onEditDescription: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (tool.enabled) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with name and toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tool.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
                
                Switch(
                    checked = tool.enabled,
                    onCheckedChange = { onToggle() }
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Description
            val displayDescription = tool.customDescription ?: tool.originalDescription
            Text(
                text = displayDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Custom description indicator
            if (tool.customDescription != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "✏️ Descrizione personalizzata",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Edit description button
            OutlinedButton(
                onClick = onEditDescription,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Modifica descrizione",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Modifica Descrizione")
            }
        }
    }
}

@Composable
private fun EditDescriptionDialog(
    tool: McpToolsViewModel.ToolUiState,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit,
    onReset: () -> Unit
) {
    var editedDescription by remember { 
        mutableStateOf(tool.customDescription ?: tool.originalDescription) 
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Modifica Descrizione",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = tool.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
                
                // Original description (reference)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Descrizione originale:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = tool.originalDescription,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                // Editable description
                OutlinedTextField(
                    value = editedDescription,
                    onValueChange = { editedDescription = it },
                    label = { Text("Nuova descrizione") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6
                )
                
                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (tool.customDescription != null) {
                        OutlinedButton(
                            onClick = onReset,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reset")
                        }
                    }
                    
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Annulla")
                    }
                    
                    Button(
                        onClick = { 
                            val finalDescription = editedDescription.takeIf { 
                                it.isNotBlank() && it != tool.originalDescription 
                            }
                            onSave(finalDescription) 
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Salva")
                    }
                }
            }
        }
    }
}
