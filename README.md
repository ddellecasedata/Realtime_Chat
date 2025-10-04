# Realtime Chat - Android App

App Android che integra le **Realtime API di OpenAI** per creare un assistente vocale intelligente con supporto per immagini e server MCP (Model Context Protocol).

## Caratteristiche

- **Chat Vocale in Tempo Reale**: Utilizza le Realtime API di OpenAI per conversazioni vocali fluide
- **Acquisizione Immagini**: Scatta foto e inviale al contesto della conversazione
- **Integrazione MCP Completa**: Connetti server MCP esterni (SQL, API, tools) e usali vocalmente! 
- **Tool Calling Automatico**: L'assistente usa tools esterni in modo intelligente
- **Configurazione MCP**: Gestisci server MCP per estendere le capacità dell'assistente
- **Autenticazione Sicura**: Gestione sicura delle API Key
- **UI Moderna**: Interfaccia pulita costruita con Jetpack Compose
- **Architettura MVVM**: Codice pulito e manutenibile
## 📋 Prerequisiti

- Android Studio Hedgehog (2023.1.1) o successivo
- Android SDK 24 o superiore
- Account OpenAI con accesso alle Realtime API
- Dispositivo Android o emulatore con supporto audio e fotocamera

## 🚀 Setup

### 1. Clona il Repository

```bash
git clone https://github.com/yourusername/Realtime_Chat.git
cd Realtime_Chat
```

### 2. Apri il Progetto in Android Studio

1. Apri Android Studio
2. Seleziona "Open an Existing Project"
3. Naviga alla cartella del progetto e selezionala

### 3. Sincronizza le Dipendenze

Android Studio sincronizzerà automaticamente le dipendenze Gradle. Se necessario, clicca su "Sync Now" nella barra degli avvisi.

### 4. Ottieni l'API Key di OpenAI

1. Vai su [platform.openai.com](https://platform.openai.com)
2. Accedi o crea un account
3. Naviga su "API Keys"
4. Crea una nuova API Key
5. Copia la chiave (la userai nell'app)

### 5. Esegui l'App

1. Collega un dispositivo Android o avvia un emulatore
2. Clicca su "Run" in Android Studio
3. L'app si installerà e si avvierà automaticamente

## 📱 Come Usare l'App

### Prima Configurazione

1. **Apri le Impostazioni**: Al primo avvio, l'app ti chiederà di configurare l'API Key
2. **Inserisci l'API Key**: Tocca l'icona delle impostazioni e inserisci la tua OpenAI API Key
3. **Aggiungi Server MCP (Opzionale)**: Configura eventuali server MCP per estendere le funzionalità
4. **Salva**: Tocca "Salva Configurazione"

### Schermata Principale

#### Bottone Audio (Microfono)
- **Avvia Audio**: Tocca per iniziare la registrazione audio
- **Stop Audio**: Tocca di nuovo per fermare e inviare l'audio all'assistente
- L'assistente risponderà vocalmente in tempo reale

#### Bottone Fotocamera
- Tocca per aprire la fotocamera
- Scatta una foto da aggiungere al contesto della conversazione
- L'immagine verrà inviata all'assistente che potrà analizzarla

## 🏗️ Architettura

### Struttura del Progetto

```
app/src/main/java/com/things5/realtimechat/
├── audio/
│   └── AudioManager.kt              # Gestione registrazione e riproduzione audio
├── data/
│   ├── Models.kt                    # Data class per modelli
│   └── SettingsRepository.kt        # Repository per le impostazioni
├── network/
│   └── RealtimeClient.kt            # Client WebSocket per Realtime API
├── ui/
│   ├── camera/
│   │   └── CameraCapture.kt         # Componente fotocamera
│   ├── screens/
│   │   ├── AdminScreen.kt           # Schermata configurazione
│   │   └── MainScreen.kt            # Schermata principale
│   └── theme/                       # Temi Material Design
├── viewmodel/
│   ├── AdminViewModel.kt            # ViewModel per Admin
│   └── MainViewModel.kt             # ViewModel principale
├── navigation/
│   └── Navigation.kt                # Sistema di navigazione
└── MainActivity.kt                  # Activity principale
```

### Componenti Principali

#### RealtimeClient
Gestisce la connessione WebSocket con le Realtime API di OpenAI:
- Connessione/disconnessione
- Invio audio, immagini e testo
- Ricezione eventi in tempo reale
- Gestione errori

#### AudioManager
Gestisce l'audio del dispositivo:
- Registrazione audio in formato PCM16 a 24kHz
- Riproduzione audio dall'assistente
- Codifica/decodifica Base64

#### SettingsRepository
Persistenza delle impostazioni usando DataStore:
- API Key OpenAI
- Configurazioni server MCP
- Stato dell'app

## 🔧 Configurazione Avanzata

### Server MCP

I server MCP (Model Context Protocol) permettono di estendere le capacità dell'assistente con strumenti esterni.

**Come aggiungere un server MCP:**

1. Vai nelle Impostazioni
2. Tocca il bottone "+"nella sezione "Server MCP"
3. Inserisci:
   - **Nome**: Nome descrittivo del server
   - **URL**: WebSocket URL (es. `ws://localhost:3000`)
4. Tocca "Aggiungi"

**Nota**: I server MCP devono essere accessibili dal dispositivo Android. Se stai testando localmente, assicurati che il server sia in esecuzione sulla stessa rete.

### Parametri Audio

L'app utilizza i seguenti parametri audio per compatibilità con le Realtime API:
- **Sample Rate**: 24000 Hz
- **Encoding**: PCM16
- **Channels**: Mono

Questi parametri sono ottimizzati per la qualità vocale e la latenza ridotta.

## 🧪 Test

Il progetto include test unitari per i componenti principali:

```bash
# Esegui i test
./gradlew test

# Esegui i test con report
./gradlew test --info
```

### Test Inclusi

- **SettingsRepositoryTest**: Test per la persistenza delle impostazioni
- **RealtimeClientTest**: Test per il client WebSocket
- **AudioManagerTest**: Test per la gestione audio

## 🔒 Sicurezza e Privacy

### Gestione API Key
- L'API Key è memorizzata in modo sicuro usando DataStore
- Non viene mai loggata o esposta
- Viene trasmessa solo tramite HTTPS alle API di OpenAI

### Permessi
L'app richiede i seguenti permessi:
- **INTERNET**: Per comunicare con le API di OpenAI
- **RECORD_AUDIO**: Per registrare la voce dell'utente
- **CAMERA**: Per acquisire immagini
- **MODIFY_AUDIO_SETTINGS**: Per ottimizzare l'audio

Tutti i permessi vengono richiesti a runtime seguendo le best practice di Android.

## 🛠️ Dipendenze Principali

- **Jetpack Compose**: UI moderna e reattiva
- **Kotlin Coroutines**: Gestione asincrona
- **OkHttp**: Client WebSocket
- **CameraX**: API fotocamera
- **DataStore**: Persistenza dati
- **Navigation Compose**: Navigazione tra schermate
- **Gson**: Serializzazione JSON

## 📚 Documentazione di Riferimento

- [OpenAI Realtime API](https://platform.openai.com/docs/guides/voice-agents)
- [OpenAI Realtime Agents SDK](https://github.com/openai/openai-agents-js)
- [Model Context Protocol](https://modelcontextprotocol.io)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [CameraX](https://developer.android.com/training/camerax)

## 🐛 Risoluzione Problemi

### L'app non si connette alle API
- Verifica che l'API Key sia corretta
- Controlla la connessione Internet
- Assicurati di avere crediti sufficienti sul tuo account OpenAI

### Audio non funziona
- Verifica che i permessi audio siano stati concessi
- Controlla le impostazioni audio del dispositivo
- Assicurati che il microfono non sia usato da altre app

### Fotocamera non funziona
- Verifica che i permessi fotocamera siano stati concessi
- Controlla che la fotocamera non sia usata da altre app
- Riavvia l'app se necessario

### Errori di Build
```bash
# Pulisci il progetto
./gradlew clean

# Ricostruisci
./gradlew build
```

## 📝 Note di Sviluppo

### Convenzioni di Codice
- Seguiamo le convenzioni di codifica Kotlin standard
- Utilizziamo l'architettura MVVM
- Tutti i file sono documentati con commenti Kdoc

### Contribuire
Contributi sono benvenuti! Per favore:
1. Fai fork del repository
2. Crea un branch per la tua feature (`git checkout -b feature/AmazingFeature`)
3. Committa le modifiche (`git commit -m 'Add some AmazingFeature'`)
4. Pusha al branch (`git push origin feature/AmazingFeature`)
5. Apri una Pull Request

## 📄 Licenza

Questo progetto è distribuito sotto licenza MIT. Vedi il file `LICENSE` per i dettagli.

## 🙏 Ringraziamenti

- OpenAI per le potenti Realtime API
- Google per Jetpack Compose e CameraX
- La community Android per le librerie open source

## 📧 Contatti

Per domande, suggerimenti o bug report, apri un issue su GitHub.

---

**Nota**: Questa app è un progetto dimostrativo. Per uso in produzione, considera ulteriori misure di sicurezza, gestione degli errori e ottimizzazioni delle performance.
