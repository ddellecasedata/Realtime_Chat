package com.things5.realtimechat.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.things5.realtimechat.data.SessionState
import com.things5.realtimechat.ui.camera.CameraCapture
import com.things5.realtimechat.viewmodel.MainViewModel
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
    onNavigateToAdmin: () -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    
    var showCamera by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    
    // Request permissions
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
    )
    
    // Initialize session when configured
    LaunchedEffect(settings.isConfigured) {
        if (settings.isConfigured && uiState.sessionState is SessionState.Idle) {
            viewModel.initializeSession()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Realtime Chat") },
                actions = {
                    // Clear conversation button (only when connected)
                    if (uiState.sessionState is SessionState.Connected) {
                        // Stop session immediately
                        IconButton(onClick = { viewModel.endSession() }) {
                            Icon(Icons.Default.Stop, "Termina Sessione")
                        }
                        
                        IconButton(
                            onClick = { viewModel.clearConversation() },
                            enabled = uiState.transcript.isNotEmpty() || uiState.capturedImages.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Delete, "Cancella Conversazione")
                        }
                    }
                    IconButton(onClick = onNavigateToAdmin) {
                        Icon(Icons.Default.Settings, "Impostazioni")
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
            if (!settings.isConfigured) {
                // Not configured state
                ConfigurationNeededCard(onNavigateToAdmin)
            } else if (showCamera) {
                // Camera view
                CameraCapture(
                    onImageCaptured = { bitmap ->
                        val base64 = bitmapToBase64(bitmap)
                        viewModel.sendImage(base64)
                        showCamera = false
                    },
                    onError = { error ->
                        // Handle error
                        showCamera = false
                    },
                    onClose = { showCamera = false }
                )
            } else {
                // Main content
                MainContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    onStartRecording = {
                        if (permissionsState.permissions[0].status.isGranted) {
                            viewModel.startAudioRecording()
                        } else {
                            showPermissionDialog = true
                        }
                    },
                    onStopRecording = { viewModel.stopAudioRecording() },
                    onOpenCamera = {
                        if (permissionsState.permissions[1].status.isGranted) {
                            showCamera = true
                        } else {
                            showPermissionDialog = true
                        }
                    },
                    onClearImages = { viewModel.clearImages() },
                    onClearError = { viewModel.clearError() }
                )
            }
        }
    }
    
    // Permission dialog
    if (showPermissionDialog) {
        PermissionDialog(
            onDismiss = { showPermissionDialog = false },
            onRequestPermissions = {
                permissionsState.launchMultiplePermissionRequest()
                showPermissionDialog = false
            }
        )
    }
}

@Composable
fun ConfigurationNeededCard(onNavigateToAdmin: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "⚙️ Configurazione Richiesta",
                    style = MaterialTheme.typography.headlineSmall
                )
                
                Text(
                    text = "Prima di utilizzare l'app, devi configurare la tua API Key di OpenAI.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Button(
                    onClick = onNavigateToAdmin,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Settings, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Vai alle Impostazioni")
                }
            }
        }
    }
}

@Composable
fun MainContent(
    uiState: com.things5.realtimechat.data.MainScreenState,
    viewModel: MainViewModel,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onOpenCamera: () -> Unit,
    onClearImages: () -> Unit,
    onClearError: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Session Status
        SessionStatusCard(uiState.sessionState)
        
        // Data Sending Indicator
        if (uiState.isSendingAudio || uiState.isSendingImage || uiState.lastDataSent != null) {
            DataSendingIndicator(
                isSendingAudio = uiState.isSendingAudio,
                isSendingImage = uiState.isSendingImage,
                lastDataSent = uiState.lastDataSent
            )
        }
        
        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Audio Button
            Button(
                onClick = {
                    if (uiState.isRecording) {
                        onStopRecording()
                    } else {
                        onStartRecording()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(120.dp),
                enabled = uiState.sessionState is SessionState.Connected,
                colors = if (uiState.isRecording) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = if (uiState.isRecording) "Termina" else "Parla",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (!uiState.isRecording) {
                        Text(
                            text = "Conversazione Full-Duplex",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Camera Button
            Button(
                onClick = onOpenCamera,
                modifier = Modifier
                    .weight(1f)
                    .height(120.dp),
                enabled = uiState.sessionState is SessionState.Connected
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Camera,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Fotocamera",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
        
        // Captured Images
        if (uiState.capturedImages.isNotEmpty()) {
            CapturedImagesCard(
                images = uiState.capturedImages,
                onClear = onClearImages
            )
        }
        
        // Transcript
        if (uiState.transcript.isNotEmpty()) {
            TranscriptCard(transcript = uiState.transcript)
        }
        
        // Error message
        if (uiState.errorMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    
                    IconButton(onClick = onClearError) {
                        Icon(
                            Icons.Default.Close,
                            "Chiudi",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SessionStatusCard(sessionState: SessionState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (sessionState) {
                is SessionState.Connected -> MaterialTheme.colorScheme.primaryContainer
                is SessionState.Connecting -> MaterialTheme.colorScheme.secondaryContainer
                is SessionState.Error -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (sessionState) {
                is SessionState.Idle -> {
                    Text("⏸️", style = MaterialTheme.typography.headlineMedium)
                    Text("In attesa", style = MaterialTheme.typography.titleMedium)
                }
                is SessionState.Connecting -> {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text("Connessione in corso...", style = MaterialTheme.typography.titleMedium)
                }
                is SessionState.Connected -> {
                    Text("✅", style = MaterialTheme.typography.headlineMedium)
                    Column {
                        Text("Connesso", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Conversazione bidirezionale attiva · L'assistente risponde automaticamente",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
                is SessionState.Error -> {
                    Text("❌", style = MaterialTheme.typography.headlineMedium)
                    Column {
                        Text("Errore", style = MaterialTheme.typography.titleMedium)
                        Text(
                            sessionState.message,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CapturedImagesCard(
    images: List<String>,
    onClear: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
                    text = "Immagini Acquisite (${images.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                
                TextButton(onClick = onClear) {
                    Text("Cancella")
                }
            }
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(images) { imageBase64 ->
                    val bitmap = base64ToBitmap(imageBase64)
                    bitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(120.dp)
                                .padding(4.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TranscriptCard(transcript: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Trascrizione",
                style = MaterialTheme.typography.titleMedium
            )
            
            Text(
                text = transcript,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun PermissionDialog(
    onDismiss: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permessi Richiesti") },
        text = {
            Text("Questa app richiede l'accesso al microfono e alla fotocamera per funzionare correttamente.")
        },
        confirmButton = {
            TextButton(onClick = onRequestPermissions) {
                Text("Concedi Permessi")
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
fun DataSendingIndicator(
    isSendingAudio: Boolean,
    isSendingImage: Boolean,
    lastDataSent: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icona animata
            if (isSendingAudio || isSendingImage) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Testo
            Column {
                Text(
                    text = when {
                        isSendingAudio -> "📤 Invio audio..."
                        isSendingImage -> "📤 Invio immagine..."
                        lastDataSent != null -> "✅ Inviato: $lastDataSent"
                        else -> "📡 Connesso"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                
                if (!isSendingAudio && !isSendingImage && lastDataSent != null) {
                    Text(
                        text = "Dati trasmessi alla Realtime API",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// Utility functions
fun bitmapToBase64(bitmap: Bitmap): String {
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
    val byteArray = outputStream.toByteArray()
    return Base64.encodeToString(byteArray, Base64.NO_WRAP)
}

fun base64ToBitmap(base64: String): Bitmap? {
    return try {
        val decodedBytes = Base64.decode(base64, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    } catch (e: Exception) {
        null
    }
}
