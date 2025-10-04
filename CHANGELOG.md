# Changelog

Tutte le modifiche notevoli a questo progetto saranno documentate in questo file.

Il formato è basato su [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
e questo progetto aderisce al [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2025-10-01

### 🎉 Release Iniziale

Prima versione stabile dell'app Realtime Chat con integrazione completa delle OpenAI Realtime API.

### ✨ Features Principali

#### Core Functionality
- **Realtime Voice Chat**: Integrazione completa con OpenAI Realtime API per chat vocale in tempo reale
- **WebSocket Connection**: Client WebSocket robusto con gestione automatica della connessione
- **Audio Streaming**: Recording e playback audio PCM16 a 24kHz ottimizzato per bassa latenza
- **Image Support**: Acquisizione e invio immagini tramite fotocamera integrata
- **MCP Server Integration**: Supporto per server Model Context Protocol personalizzati

#### UI/UX
- **Modern Material Design**: Interfaccia utente costruita con Jetpack Compose e Material Design 3
- **Admin Screen**: Schermata di configurazione intuitiva per API Key e server MCP
- **Main Screen**: Interfaccia principale con controlli audio e fotocamera facilmente accessibili
- **Real-time Feedback**: Indicatori di stato della sessione e feedback visivo per tutte le operazioni
- **Error Handling**: Gestione errori user-friendly con messaggi chiari

#### Architecture
- **MVVM Pattern**: Architettura pulita con separazione chiara delle responsabilità
- **Kotlin Coroutines**: Gestione asincrona moderna e reattiva
- **StateFlow**: State management predicibile e performante
- **DataStore**: Persistenza sicura delle impostazioni
- **Dependency Injection**: Repository pattern per gestione dati

#### Security & Privacy
- **Secure Storage**: API Key memorizzata in modo sicuro con DataStore
- **Runtime Permissions**: Gestione permessi Android moderna con richieste a runtime
- **No Data Persistence**: Audio e immagini non salvati permanentemente
- **HTTPS/WSS**: Tutte le comunicazioni criptate

#### Developer Experience
- **Unit Tests**: Test completi per repository, client e audio manager
- **Comprehensive Documentation**: README, QUICKSTART, ARCHITECTURE e guide MCP
- **Code Quality**: Codice ben documentato seguendo le Kotlin conventions
- **Build Configuration**: Setup Gradle ottimizzato con tutte le dipendenze

### 📦 Dipendenze

#### Core
- Kotlin 1.9.0
- Android SDK 24-34
- Jetpack Compose BOM 2024.01.00

#### Networking
- OkHttp 4.12.0 (WebSocket)
- Gson 2.10.1 (JSON serialization)

#### UI
- Material 3
- Navigation Compose 2.7.7
- Accompanist Permissions 0.34.0
- Coil 2.5.0 (Image loading)

#### Storage
- DataStore Preferences 1.0.0

#### Camera
- CameraX 1.3.1

#### Testing
- JUnit 4
- Robolectric 4.11.1
- MockK 1.13.8
- Coroutines Test 1.7.3

### 📱 Compatibilità

- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)
- **Compile SDK**: 34

### 📝 Documentazione

- README.md: Guida completa al progetto
- QUICKSTART.md: Guida rapida per iniziare
- ARCHITECTURE.md: Documentazione architetturale dettagliata
- MCP_EXAMPLES.md: Esempi e guida server MCP
- CONTRIBUTING.md: Linee guida per contribuire
- FAQ.md: Domande frequenti

### 🔧 File di Configurazione

- `.gitignore`: Configurazione Git completa per Android
- `LICENSE`: Licenza MIT
- `build.gradle.kts`: Configurazione build con tutte le dipendenze

### 🐛 Known Issues

- La fotocamera potrebbe non funzionare correttamente su alcuni emulatori (testare su dispositivo fisico)
- Il rilevamento automatico della fine del parlato potrebbe variare in base al rumore ambientale
- Server MCP devono essere accessibili sulla stessa rete del dispositivo

### 🔮 Future Enhancements

Vedi la sezione "Future Improvements" in ARCHITECTURE.md per la roadmap completa.

### 👥 Contributors

- Initial Development Team

---

## [Unreleased]

Modifiche in sviluppo per la prossima release.

### Planned
- [ ] Dark mode support
- [ ] Conversation history
- [ ] Export chat transcripts
- [ ] Multiple API key profiles
- [ ] Improved error recovery
- [ ] Background audio support

---

**Note**: Per aggiornamenti e nuove feature, segui il repository su GitHub.
