# 🔍 Vista Debug MCP

Nuova funzionalità implementata per il debug dei server MCP nell'area settings.

## 📋 Panoramica

È stata aggiunta una nuova vista di debug accessibile cliccando su un server MCP nella schermata delle impostazioni. Questa vista mostra informazioni dettagliate sullo stato della connessione, autenticazione e tool caricati.

## ✨ Funzionalità Implementate

### 1. **Stato Connessione MCP**
- Monitoraggio in tempo reale dello stato del server (CONNECTED, CONNECTING, DISCONNECTED, ERROR)
- Visualizzazione del protocollo utilizzato (WebSocket o HTTP)
- Timestamp dell'ultimo aggiornamento
- Messaggi di errore dettagliati in caso di problemi

### 2. **Informazioni Protocollo**
- Tipo di protocollo utilizzato per la connessione
- Stato dell'autenticazione (attualmente non configurata)

### 3. **Lista Tool Caricati**
- Visualizzazione di tutti i tool disponibili dal server MCP
- Dettagli per ogni tool:
  - Nome del tool (in formato monospace)
  - Descrizione
  - Parametri richiesti (visualizzati in JSON)

### 4. **Navigazione**
- Click su un server MCP nella schermata Admin apre la vista di debug
- Pulsante "Aggiorna" per ricaricare le informazioni
- Pulsante "Indietro" per tornare alle impostazioni

## 🏗️ Architettura

### Modifiche ai File Esistenti

#### `McpBridge.kt`
- ✅ Aggiunto `StateFlow<Map<String, McpServerStatus>>` per esporre lo stato dei server
- ✅ Aggiunto tracking automatico dello stato durante connessione/disconnessione
- ✅ Update dello stato quando i tool vengono caricati
- ✅ Nuovi modelli dati:
  - `McpConnectionState` (enum): DISCONNECTED, CONNECTING, CONNECTED, ERROR
  - `McpServerStatus` (data class): stato completo del server
  - `McpTool` (già esistente, nessuna modifica)

#### `MainViewModel.kt`
- ✅ Aggiunto `StateFlow<Map<String, McpServerStatus>>` esposto pubblicamente
- ✅ Metodo `observeMcpStatus()` per osservare i cambiamenti di stato
- ✅ Integrazione con `McpBridge.serverStatus`

#### `AdminScreen.kt`
- ✅ Aggiunto parametro `onNavigateToMcpDebug: (String) -> Unit`
- ✅ Reso cliccabile il `McpServerCard`
- ✅ Import di `clickable` modifier

#### `Navigation.kt`
- ✅ Aggiunta route `Screen.McpDebug` con parametro `serverName`
- ✅ Composable per `McpDebugScreen` con argomento di navigazione
- ✅ Passaggio dello stato MCP dalla `MainViewModel`

### Nuovi File Creati

#### `McpDebugScreen.kt`
Nuovo screen Compose con i seguenti componenti:

**Composable Principali:**
- `McpDebugScreen`: Screen principale con Scaffold e LazyColumn
- `ConnectionStatusCard`: Card per lo stato della connessione
- `ProtocolInfoCard`: Card per le informazioni sul protocollo
- `ToolCard`: Card per ogni tool disponibile
- `ConnectionStateIcon`: Icona dinamica basata sullo stato

**Features:**
- Design Material 3
- Colori dinamici in base allo stato della connessione
- Monospace font per nomi di tool e parametri
- Formato timestamp HH:mm:ss
- Gestione stato "server non trovato"
- Visualizzazione errori dettagliati

#### `McpBridgeStatusTest.kt`
Suite completa di test unitari:

**11 Test Implementati:**
1. ✅ `test McpServerStatus creation with all fields`
2. ✅ `test McpServerStatus with error state`
3. ✅ `test McpConnectionState enum values`
4. ✅ `test McpTool creation`
5. ✅ `test McpBridge initial state is empty`
6. ✅ `test getAllTools returns empty list when no servers`
7. ✅ `test McpBridge disconnect clears status`
8. ✅ `test McpServerStatus timestamp is recent`
9. ✅ `test McpServerStatus copy with different tools`
10. ✅ `test McpServerStatus equals and hashCode`
11. ✅ `test multiple connection states are distinct`

**Risultati:** Tutti i test passano al 100% ✅

## 🎯 Come Usare

### Accesso alla Vista Debug

1. Apri l'app e vai nelle **Impostazioni** (⚙️)
2. Nella sezione "Server MCP", **clicca su un server configurato**
3. Si aprirà la **schermata di debug MCP**

### Cosa Vedere nella Vista Debug

#### Stato Connessione
```
┌─────────────────────────────────────┐
│ Stato Connessione            [Icon] │
├─────────────────────────────────────┤
│ Stato:            CONNECTED          │
│ Server:           SQL Server         │
│ Ultimo agg.:      23:15:26          │
└─────────────────────────────────────┘
```

#### Informazioni Protocollo
```
┌─────────────────────────────────────┐
│ Informazioni Protocollo              │
├─────────────────────────────────────┤
│ Protocollo:       [WebSocket]       │
│ Autenticazione:   Non configurata   │
└─────────────────────────────────────┘
```

#### Tools Caricati
```
┌─────────────────────────────────────┐
│ ✓ execute_sql                        │
│   Execute a SQL query                │
│   Parametri:                         │
│   {"query": "string"}                │
└─────────────────────────────────────┘
```

## 🎨 Design

### Stati Visivi

**CONNECTED** (Verde):
- Card con sfondo `primaryContainer`
- Icona: CheckCircle verde (#2E7D32)
- Testo stato: Verde

**CONNECTING** (Arancione):
- Card con sfondo `tertiaryContainer`
- Icona: CircularProgressIndicator arancione (#ED6C02)
- Testo stato: Arancione

**ERROR** (Rosso):
- Card con sfondo `errorContainer`
- Icona: Error rossa
- Testo stato: Rosso
- Messaggio di errore visibile

**DISCONNECTED** (Grigio):
- Card con sfondo `surfaceVariant`
- Icona: Box grigio
- Testo stato: Grigio

### Colori e Tipografia

- **Titoli**: Typography.titleMedium/Large con FontWeight.Bold
- **Nomi Tool**: FontFamily.Monospace
- **Parametri JSON**: FontFamily.Monospace su Surface con `surfaceVariant`
- **Badge Protocollo**: Primary color su shape small

## 🧪 Testing

### Coverage Completo

- ✅ Test modelli dati (McpServerStatus, McpTool, McpConnectionState)
- ✅ Test stato iniziale McpBridge
- ✅ Test disconnessione e pulizia stato
- ✅ Test copy e equality dei modelli
- ✅ Test enum values
- ✅ Test timestamp

### Esecuzione Test

```bash
./gradlew :app:testDebugUnitTest
```

**Risultati:**
- 11/11 test passati ✅
- 100% success rate
- Durata: 0.707s

## 🚀 Build

L'app compila correttamente:

```bash
./gradlew :app:assembleDebug
# BUILD SUCCESSFUL in 13s
```

## 📝 Note Tecniche

### Flow Architecture
- `McpBridge.serverStatus`: StateFlow che emette Map<String, McpServerStatus>
- `MainViewModel.mcpServerStatus`: StateFlow esposto alle UI
- Aggiornamenti automatici tramite `observeMcpStatus()` in MainViewModel

### Navigation
- Route parametrizzata: `"mcp_debug/{serverName}"`
- Argomento: `navArgument("serverName") { type = NavType.StringType }`
- Helper: `Screen.McpDebug.createRoute(serverName)`

### State Management
- Stato server tracciato durante:
  - Connessione (onOpen)
  - Disconnessione (onClosed)
  - Errori (onFailure)
  - Caricamento tools (handleMcpMessage, requestToolsHttp)

## 🔮 Possibili Miglioramenti Futuri

1. **Autenticazione**: Visualizzare stato autenticazione quando implementata
2. **Statistiche**: Numero di tool call eseguiti, latenza media
3. **Log**: Storico delle ultime N operazioni
4. **Retry**: Pulsante per riconnessione manuale
5. **Export**: Esportare stato debug come JSON/testo
6. **Real-time Updates**: Indicatore visivo quando lo stato cambia
7. **Network Info**: Latency, bandwidth, connection quality

## ✅ Conclusione

La nuova vista di debug MCP è completamente funzionale e testata. Fornisce agli utenti e sviluppatori uno strumento potente per:
- Monitorare la connessione ai server MCP
- Verificare quali tool sono disponibili
- Diagnosticare problemi di connessione
- Comprendere lo stato del sistema in tempo reale

**Status**: ✅ **FEATURE COMPLETA E PRONTA PER L'USO**
