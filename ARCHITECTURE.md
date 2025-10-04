# 🏗️ Architettura dell'App - Realtime Chat

Documentazione tecnica dell'architettura dell'applicazione Android.

## 📐 Pattern Architetturale

L'app utilizza il pattern **MVVM (Model-View-ViewModel)** con le seguenti caratteristiche:

- **Clean Architecture**: Separazione chiara tra layers
- **Unidirectional Data Flow**: Flusso dati predicibile
- **Reactive Programming**: Utilizzo di Kotlin Flow
- **Dependency Injection**: Repository pattern

## 🔷 Layers

### 1. Presentation Layer (UI)

#### Composables
- `MainScreen`: Schermata principale con controlli audio e fotocamera
- `AdminScreen`: Configurazione e gestione impostazioni
- `CameraCapture`: Componente per acquisizione immagini

#### ViewModels
- `MainViewModel`: Gestisce stato e logica della schermata principale
- `AdminViewModel`: Gestisce configurazione e impostazioni

**Responsabilità**:
- Rendering UI
- Gestione eventi utente
- Osservazione dello stato
- Navigazione

### 2. Domain Layer

#### Models
```kotlin
// Configurazione app
data class AppSettings(
    val openAiApiKey: String,
    val mcpServers: List<McpServerConfig>,
    val isConfigured: Boolean
)

// Stato sessione
sealed class SessionState {
    object Idle
    object Connecting
    object Connected
    data class Error(val message: String)
}
```

**Responsabilità**:
- Definizione entità business
- Regole di validazione
- Stati dell'applicazione

### 3. Data Layer

#### Repository
- `SettingsRepository`: Gestione persistenza con DataStore

#### Network
- `RealtimeClient`: Client WebSocket per OpenAI Realtime API

#### Audio
- `AudioManager`: Gestione recording e playback PCM16

**Responsabilità**:
- Accesso ai dati
- Comunicazione network
- Cache e persistenza
- Gestione audio

## 🔄 Flusso dei Dati

```
┌─────────────────────────────────────────────┐
│              User Action                    │
└────────────────┬────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────┐
│           UI (Composable)                   │
│  • MainScreen                               │
│  • AdminScreen                              │
│  • CameraCapture                            │
└────────────────┬────────────────────────────┘
                 │ Event
                 ▼
┌─────────────────────────────────────────────┐
│          ViewModel                          │
│  • Gestisce logica business                │
│  • Aggiorna UI State                        │
│  • Coordina repository                      │
└────────────────┬────────────────────────────┘
                 │
                 ├──────────────┬──────────────┐
                 ▼              ▼              ▼
    ┌────────────────┐ ┌──────────────┐ ┌──────────────┐
    │ Repository     │ │ RealtimeClient│ │ AudioManager │
    │ (DataStore)    │ │ (WebSocket)   │ │ (Audio)      │
    └────────────────┘ └──────────────┘ └──────────────┘
                 │              │              │
                 └──────────────┴──────────────┘
                                │
                                ▼ StateFlow
                    ┌───────────────────────┐
                    │    UI State Update    │
                    └───────────────────────┘
                                │
                                ▼
                    ┌───────────────────────┐
                    │    UI Recomposition   │
                    └───────────────────────┘
```

## 🌊 State Management

### State Flow Architecture

```kotlin
// UI State
data class MainScreenState(
    val sessionState: SessionState,
    val isRecording: Boolean,
    val transcript: String,
    val capturedImages: List<String>,
    val errorMessage: String?
)

// ViewModel espone StateFlow
val uiState: StateFlow<MainScreenState>

// UI osserva lo stato
val state by viewModel.uiState.collectAsState()
```

### Event Handling

```kotlin
// UI invia eventi al ViewModel
viewModel.startAudioRecording()
viewModel.sendImage(imageBase64)

// ViewModel processa ed aggiorna lo stato
private fun updateState(...)
```

## 🔌 Integrazione Realtime API

### WebSocket Connection

```
┌──────────────┐                    ┌──────────────┐
│   Android    │                    │   OpenAI     │
│     App      │                    │ Realtime API │
└──────┬───────┘                    └───────┬──────┘
       │                                    │
       │  1. Connect (WSS)                 │
       ├───────────────────────────────────>│
       │                                    │
       │  2. Session Created                │
       │<───────────────────────────────────┤
       │                                    │
       │  3. Update Session Config          │
       ├───────────────────────────────────>│
       │                                    │
       │  4. Audio Stream (Base64)          │
       ├───────────────────────────────────>│
       │                                    │
       │  5. Response Audio Delta           │
       │<───────────────────────────────────┤
       │                                    │
       │  6. Send Image (Base64)            │
       ├───────────────────────────────────>│
       │                                    │
       │  7. Text/Audio Response            │
       │<───────────────────────────────────┤
       │                                    │
```

### Audio Pipeline

```
Microphone
    │
    ▼
┌────────────────┐
│ AudioRecord    │  24kHz, PCM16, Mono
│ (Android API)  │
└────────┬───────┘
         │ ByteArray
         ▼
┌────────────────┐
│ Base64 Encode  │
└────────┬───────┘
         │ String
         ▼
┌────────────────┐
│ RealtimeClient │  WebSocket
│ .sendAudio()   │
└────────┬───────┘
         │
         ▼
    OpenAI API
         │
         ▼
┌────────────────┐
│ Response Delta │
└────────┬───────┘
         │ Base64 String
         ▼
┌────────────────┐
│ Base64 Decode  │
└────────┬───────┘
         │ ByteArray
         ▼
┌────────────────┐
│ AudioTrack     │
│ (Android API)  │
└────────┬───────┘
         │
         ▼
     Speaker
```

## 📦 Dependency Management

### Core Dependencies

```kotlin
// UI
androidx.compose.material3
androidx.navigation.compose

// Architecture
androidx.lifecycle.viewmodel-compose
kotlinx.coroutines

// Network
okhttp3 (WebSocket)
gson (JSON)

// Storage
androidx.datastore.preferences

// Camera
androidx.camera.*

// Permissions
accompanist-permissions
```

## 🔐 Security Considerations

### API Key Storage
```kotlin
// Stored in DataStore (encrypted by system)
// Never logged or exposed in UI
// Transmitted only over HTTPS
```

### Audio Data
- Audio non persistito su disco
- Trasmesso direttamente via WebSocket
- Cancellato dalla memoria dopo l'uso

### Image Data
- Immagini convertite in Base64
- Non salvate permanentemente
- Cancellabili dall'utente

## 🧪 Testing Strategy

### Unit Tests
- **SettingsRepositoryTest**: Test persistenza dati
- **RealtimeClientTest**: Test comunicazione WebSocket
- **AudioManagerTest**: Test gestione audio

### Integration Tests
- Test del flusso completo da UI a API
- Test gestione errori

### UI Tests (Future)
- Test interazione utente
- Test navigazione
- Test stati UI

## 🚀 Performance Optimization

### Audio Streaming
- Buffer size ottimizzato per latenza ridotta
- Streaming continuo senza buffering completo
- Gestione efficiente della memoria

### Image Processing
- Compressione JPEG al 90%
- Ridimensionamento automatico se necessario
- Conversione Base64 ottimizzata

### State Management
- StateFlow per evitare recomposizioni non necessarie
- remember per valori calcolati costosi
- LaunchedEffect per operazioni async

## 🔄 Lifecycle Management

### Activity Lifecycle
```kotlin
MainActivity
    │
    ├── onCreate
    │   └── setContent { AppNavigation() }
    │
    ├── onStart
    │   └── ViewModels initialized
    │
    ├── onResume
    │   └── Audio resources acquired
    │
    ├── onPause
    │   └── Audio stopped
    │
    └── onDestroy
        └── Resources released
```

### ViewModel Lifecycle
```kotlin
ViewModel
    │
    ├── init
    │   └── Load settings
    │
    ├── User interactions
    │   └── State updates
    │
    └── onCleared
        └── Release resources
```

## 📊 Error Handling

### Strategie
1. **Try-Catch**: Per operazioni sincrone
2. **Flow Error Handling**: Per stream reattivi
3. **UI Feedback**: Messaggi di errore user-friendly
4. **Logging**: Per debug e monitoring

### Error Types
- `NetworkError`: Problemi di connessione
- `AuthError`: API Key invalida
- `AudioError`: Problemi microfono/speaker
- `CameraError`: Problemi fotocamera

## 🎯 Future Improvements

### Short Term
- [ ] Caching delle risposte
- [ ] Offline mode per configurazione
- [ ] Tema scuro/chiaro

### Medium Term
- [ ] History delle conversazioni
- [ ] Export conversazioni
- [ ] Multiple API key profiles

### Long Term
- [ ] Backend server per gestione avanzata
- [ ] Analytics e monitoring
- [ ] Widget home screen

## 📚 Riferimenti

- [Android Architecture Components](https://developer.android.com/topic/architecture)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
- [OpenAI Realtime API](https://platform.openai.com/docs/guides/voice-agents)

---

**Nota**: Questa architettura è progettata per scalabilità e manutenibilità. Segue le best practice Android moderne e può essere estesa facilmente per nuove funzionalità.
