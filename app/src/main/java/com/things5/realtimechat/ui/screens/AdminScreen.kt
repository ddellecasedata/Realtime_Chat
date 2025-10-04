package com.things5.realtimechat.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.things5.realtimechat.data.McpAuthType
import com.things5.realtimechat.data.McpServerConfig
import com.things5.realtimechat.data.Things5ConnectionStatus
import com.things5.realtimechat.viewmodel.AdminViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMcpDebug: (String) -> Unit,
    onNavigateToMcpTools: (String) -> Unit,
    onNavigateToRealtimeDebug: () -> Unit,
    viewModel: AdminViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    val isTestingThings5 by viewModel.isTestingThings5.collectAsState()
    val things5Status by viewModel.things5ConnectionStatus.collectAsState()
    
    var showAddServerDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }
    
    // Things5 state
    var things5Username by remember { mutableStateOf("") }
    var things5Password by remember { mutableStateOf("") }
    var things5ServerUrl by remember { mutableStateOf("") }
    var showThings5Password by remember { mutableStateOf(false) }
    
    LaunchedEffect(settings.openAiApiKey) {
        apiKey = settings.openAiApiKey
    }
    
    LaunchedEffect(settings.things5Config) {
        things5Username = settings.things5Config.username
        things5Password = settings.things5Config.password
        things5ServerUrl = settings.things5Config.serverUrl
    }
    
    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            viewModel.resetSaveSuccess()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurazione Admin") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Indietro")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Debug Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Debug Realtime API",
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        Text(
                            text = "Monitora lo stato della connessione, l'utilizzo e i log delle operazioni",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Button(
                            onClick = onNavigateToRealtimeDebug,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Visibility, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Apri Debug Realtime")
                        }
                    }
                }
            }
            
            // API Key Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "OpenAI API Key",
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = {
                                apiKey = it
                                viewModel.updateApiKey(it)
                            },
                            label = { Text("API Key") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (showApiKey) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                TextButton(onClick = { showApiKey = !showApiKey }) {
                                    Text(if (showApiKey) "Nascondi" else "Mostra")
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password
                            )
                        )
                        
                        Text(
                            text = "Inserisci la tua API Key di OpenAI per abilitare le Realtime API",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Things5 Integration Section
            item {
                Things5IntegrationCard(
                    config = settings.things5Config,
                    username = things5Username,
                    password = things5Password,
                    serverUrl = things5ServerUrl,
                    showPassword = showThings5Password,
                    isTestingConnection = isTestingThings5,
                    connectionStatus = things5Status,
                    onUsernameChange = { 
                        things5Username = it
                        viewModel.updateThings5Credentials(it, things5Password)
                    },
                    onPasswordChange = { 
                        things5Password = it
                        viewModel.updateThings5Credentials(things5Username, it)
                    },
                    onServerUrlChange = {
                        things5ServerUrl = it
                        viewModel.updateThings5ServerUrl(it)
                    },
                    onTogglePasswordVisibility = { showThings5Password = !showThings5Password },
                    onToggleEnabled = { viewModel.toggleThings5Integration() },
                    onTestConnection = { viewModel.testThings5Connection() },
                    onStatusDescription = { status -> viewModel.getThings5StatusDescription(status) }
                )
            }
            
            // MCP Servers Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Server MCP",
                                style = MaterialTheme.typography.titleMedium
                            )
                            
                            IconButton(onClick = { showAddServerDialog = true }) {
                                Icon(Icons.Default.Add, "Aggiungi Server")
                            }
                        }
                        
                        Text(
                            text = "Configura i server MCP per estendere le funzionalità dell'assistente",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // MCP Servers List
            items(settings.mcpServers) { server ->
                McpServerCard(
                    server = server,
                    onToggle = { viewModel.toggleMcpServer(server.name) },
                    onDelete = { viewModel.removeMcpServer(server.name) },
                    onManageTools = { onNavigateToMcpTools(server.name) },
                    onClick = { onNavigateToMcpDebug(server.name) }
                )
            }
            
            // Save Button
            item {
                Button(
                    onClick = { viewModel.saveSettings() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving && apiKey.isNotEmpty()
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Salva Configurazione")
                    }
                }
            }
            
            // Info Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "ℹ️ Informazioni",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Questa app utilizza le Realtime API di OpenAI per fornire un'esperienza di chat vocale in tempo reale. " +
                                    "I server MCP (Model Context Protocol) permettono di estendere le capacità dell'assistente con strumenti esterni.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
    
    // Add Server Dialog
    if (showAddServerDialog) {
        AddMcpServerDialog(
            onDismiss = { showAddServerDialog = false },
            onAdd = { server ->
                viewModel.addMcpServer(server)
                showAddServerDialog = false
            }
        )
    }
    
    // Success Snackbar
    if (saveSuccess) {
        Snackbar(
            modifier = Modifier.padding(16.dp)
        ) {
            Text("Configurazione salvata con successo!")
        }
    }
}

@Composable
fun McpServerCard(
    server: McpServerConfig,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onManageTools: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Main row with server info and switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = server.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = server.enabled,
                        onCheckedChange = { onToggle() }
                    )
                    
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            "Elimina",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            // Manage tools button
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onManageTools)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Build,
                    contentDescription = "Gestisci tool",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Gestisci Tool",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMcpServerDialog(
    onDismiss: () -> Unit,
    onAdd: (McpServerConfig) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var authType by remember { mutableStateOf(McpAuthType.NONE) }
    var apiKey by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var customHeaderName by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aggiungi Server MCP") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("wss://server.example.com/mcp") },
                    singleLine = true
                )
                
                // Authentication Type Dropdown
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = when(authType) {
                            McpAuthType.NONE -> "Nessuna"
                            McpAuthType.BEARER -> "Bearer Token"
                            McpAuthType.API_KEY -> "API Key"
                            McpAuthType.BASIC -> "Basic Auth"
                            McpAuthType.CUSTOM -> "Custom Header"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Autenticazione") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Nessuna") },
                            onClick = { authType = McpAuthType.NONE; expanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Bearer Token") },
                            onClick = { authType = McpAuthType.BEARER; expanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("API Key") },
                            onClick = { authType = McpAuthType.API_KEY; expanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Basic Auth") },
                            onClick = { authType = McpAuthType.BASIC; expanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Custom Header") },
                            onClick = { authType = McpAuthType.CUSTOM; expanded = false }
                        )
                    }
                }
                
                // Auth fields based on type
                when (authType) {
                    McpAuthType.BEARER, McpAuthType.API_KEY -> {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text(if (authType == McpAuthType.BEARER) "Token" else "API Key") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (showApiKey) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(
                                        imageVector = if (showApiKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (showApiKey) "Nascondi" else "Mostra"
                                    )
                                }
                            }
                        )
                    }
                    McpAuthType.BASIC -> {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (showPassword) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (showPassword) "Nascondi" else "Mostra"
                                    )
                                }
                            }
                        )
                    }
                    McpAuthType.CUSTOM -> {
                        OutlinedTextField(
                            value = customHeaderName,
                            onValueChange = { customHeaderName = it },
                            label = { Text("Nome Header") },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("es: X-Custom-Auth") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("Valore") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (showApiKey) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(
                                        imageVector = if (showApiKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (showApiKey) "Nascondi" else "Mostra"
                                    )
                                }
                            }
                        )
                    }
                    McpAuthType.NONE -> {
                        // No auth fields
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotEmpty() && url.isNotEmpty()) {
                        onAdd(
                            McpServerConfig(
                                name = name,
                                url = url,
                                authType = authType,
                                apiKey = apiKey,
                                username = username,
                                password = password,
                                customHeaderName = customHeaderName
                            )
                        )
                    }
                },
                enabled = name.isNotEmpty() && url.isNotEmpty()
            ) {
                Text("Aggiungi")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla")
            }
        }
    )
}

@Composable
fun Things5IntegrationCard(
    config: com.things5.realtimechat.data.Things5Config,
    username: String,
    password: String,
    serverUrl: String,
    showPassword: Boolean,
    isTestingConnection: Boolean,
    connectionStatus: Things5ConnectionStatus,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onServerUrlChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onToggleEnabled: () -> Unit,
    onTestConnection: () -> Unit,
    onStatusDescription: (Things5ConnectionStatus) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (config.enabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "🏠 Integrazione Things5 IoT",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Connetti i tuoi dispositivi IoT Things5",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Switch(
                    checked = config.enabled,
                    onCheckedChange = { onToggleEnabled() }
                )
            }
            
            if (config.enabled) {
                // Connection status indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val (icon, color) = when (connectionStatus) {
                        Things5ConnectionStatus.CONNECTED -> Icons.Default.CheckCircle to Color.Green
                        Things5ConnectionStatus.CONNECTING -> Icons.Default.CloudSync to MaterialTheme.colorScheme.primary
                        Things5ConnectionStatus.ERROR -> Icons.Default.Error to MaterialTheme.colorScheme.error
                        Things5ConnectionStatus.AUTHENTICATION_FAILED -> Icons.Default.Warning to MaterialTheme.colorScheme.error
                        Things5ConnectionStatus.DISCONNECTED -> Icons.Default.CloudSync to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                    
                    Text(
                        text = onStatusDescription(connectionStatus),
                        style = MaterialTheme.typography.bodyMedium,
                        color = color
                    )
                }
                
                // Server URL (editable)
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = onServerUrlChange,
                    label = { Text("Server MCP URL") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://things5-mcp-server.onrender.com/mcp") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri
                    ),
                    isError = serverUrl.isNotEmpty() && (!serverUrl.startsWith("http") || !serverUrl.endsWith("/mcp")),
                    supportingText = {
                        if (serverUrl.isNotEmpty() && (!serverUrl.startsWith("http") || !serverUrl.endsWith("/mcp"))) {
                            Text(
                                text = "L'URL deve iniziare con http/https e finire con /mcp",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                )
                
                // Username field
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    label = { Text("Username Things5") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("user@example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email
                    ),
                    isError = username.isNotEmpty() && !username.contains("@")
                )
                
                // Password field
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("Password Things5") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = onTogglePasswordVisibility) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (showPassword) "Nascondi password" else "Mostra password"
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password
                    ),
                    isError = password.isNotEmpty() && password.length < 6
                )
                
                // Test connection button
                Button(
                    onClick = onTestConnection,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isTestingConnection && 
                             username.isNotEmpty() && 
                             password.isNotEmpty() && 
                             serverUrl.isNotEmpty() && 
                             serverUrl.startsWith("http") && 
                             serverUrl.endsWith("/mcp"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when (connectionStatus) {
                            Things5ConnectionStatus.CONNECTED -> Color.Green
                            Things5ConnectionStatus.ERROR, Things5ConnectionStatus.AUTHENTICATION_FAILED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                ) {
                    if (isTestingConnection) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Test in corso...")
                    } else {
                        Icon(Icons.Default.CloudSync, null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when (connectionStatus) {
                                Things5ConnectionStatus.CONNECTED -> "✅ Connesso"
                                Things5ConnectionStatus.ERROR -> "🔄 Riprova"
                                Things5ConnectionStatus.AUTHENTICATION_FAILED -> "🔄 Verifica credenziali"
                                else -> "🔗 Testa connessione"
                            }
                        )
                    }
                }
                
                // Help text
                Text(
                    text = "💡 Configura l'URL del server MCP Things5 (deve finire con /mcp) e le tue credenziali. " +
                            "Il server MCP gestirà automaticamente l'autenticazione OAuth e le chiamate API.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Last connection info
                config.lastSuccessfulConnection?.let { timestamp ->
                    val timeAgo = System.currentTimeMillis() - timestamp
                    val minutes = timeAgo / (1000 * 60)
                    val hours = minutes / 60
                    val days = hours / 24
                    
                    val timeText = when {
                        days > 0 -> "${days}d fa"
                        hours > 0 -> "${hours}h fa"
                        minutes > 0 -> "${minutes}m fa"
                        else -> "Ora"
                    }
                    
                    Text(
                        text = "🕒 Ultima connessione: $timeText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
