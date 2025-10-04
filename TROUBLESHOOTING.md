# 🔧 Troubleshooting - Realtime Chat

## Problemi Comuni e Soluzioni

### 1. App si Chiude Immediatamente (Crash)

**Possibili cause**:
- Mancanza di permessi
- MultiDex non configurato
- Errore di inizializzazione

**Soluzioni**:
1. Verificare log con Logcat:
   ```bash
   adb logcat -d | grep "AndroidRuntime"
   ```

2. Reinstallare l'app:
   ```bash
   ./gradlew uninstallDebug
   ./gradlew installDebug
   ```

3. Verificare che i permessi siano stati concessi

### 2. Schermata Bianca/Nera

**Possibili cause**:
- Errore in Compose
- Tema non caricato
- Errore di navigazione

**Soluzioni**:
1. Controllare log Compose:
   ```bash
   adb logcat -d | grep "Compose"
   ```

2. Verificare che MyApplicationTheme sia configurato

### 3. Connessione Realtime API Fallisce

**Errore**: "Cannot send event: not connected"

**Soluzioni**:
1. Verificare API Key nelle impostazioni
2. Verificare connessione internet
3. Verificare permesso INTERNET nel Manifest

### 4. Audio Non Funziona

**Errore**: "AudioRecord failed to start"

**Soluzioni**:
1. Concedere permesso RECORD_AUDIO:
   - Android 6+: Richiesto runtime permission
   - Settings → App → Realtime Chat → Permissions → Microphone → Allow

2. Verificare che il microfono non sia usato da altre app

### 5. Camera Non Si Apre

**Errore**: "CameraX initialization failed"

**Soluzioni**:
1. Concedere permesso CAMERA
2. Verificare che CameraX sia inizializzato:
   ```kotlin
   ProcessCameraProvider.getInstance(context)
   ```

### 6. MCP Server Non Si Connette

**Errore**: "MCP Server connection failed"

**Soluzioni**:
1. Verificare URL server (deve iniziare con `ws://` o `wss://`)
2. Verificare che il server sia raggiungibile
3. Testare con:
   ```bash
   curl -i -N -H "Connection: Upgrade" \
        -H "Upgrade: websocket" \
        https://sql-mcp-server.onrender.com
   ```

4. Controllare firewall/network

### 7. MultiDex Error

**Errore**: "com.android.dex.DexIndexOverflowException"

**Soluzione**:
Già risolto con:
```gradle
defaultConfig {
    multiDexEnabled = true
}
dependencies {
    implementation("androidx.multidex:multidex:2.0.1")
}
```

### 8. Gradle Build Bloccato

**Sintomo**: Build ferma al 64% (mergeExtDexDebug)

**Soluzioni**:
1. Killare processo:
   ```bash
   pkill -f gradle
   ```

2. Build con opzioni ottimizzate:
   ```bash
   ./gradlew assembleDebug --no-daemon --max-workers=2
   ```

3. Pulire cache:
   ```bash
   ./gradlew clean
   rm -rf ~/.gradle/caches
   ```

## 🐛 Debug Logging

### Abilitare Log Dettagliati

1. **Realtime Client**:
   ```kotlin
   // In RealtimeClient.kt, già abilitato:
   Log.d(TAG, "WebSocket connected")
   Log.d(TAG, "Received message: $text")
   ```

2. **MCP Bridge**:
   ```kotlin
   // In McpBridge.kt, già abilitato:
   Log.d(TAG, "Connecting to MCP server: ${server.name}")
   Log.d(TAG, "Registered ${tools.size} tools from $serverName")
   ```

3. **MainViewModel**:
   ```kotlin
   // In MainViewModel.kt, già abilitato:
   Log.d(TAG, "Session initialized successfully")
   Log.d(TAG, "Tool call: ${event.toolName}")
   ```

### Leggere Log in Tempo Reale

```bash
# Tutti i log dell'app
adb logcat | grep -E "RealtimeClient|McpBridge|MainViewModel"

# Solo errori
adb logcat | grep -E "ERROR|FATAL"

# Log specifici
adb logcat -s RealtimeClient:D McpBridge:D
```

## 🧪 Test Manuali

### Test 1: Verifica Installazione
```bash
./gradlew installDebug
adb shell am start -n com.things5.realtimechat/.MainActivity
```

### Test 2: Verifica Permessi
```bash
adb shell dumpsys package com.things5.realtimechat | grep permission
```

### Test 3: Verifica Network
```bash
adb shell ping -c 3 api.openai.com
```

## 📞 Supporto

Se il problema persiste:

1. **Cattura log completi**:
   ```bash
   adb logcat -d > app_log.txt
   ```

2. **Screenshot dell'errore**

3. **Dettagli dispositivo**:
   - Modello
   - Versione Android
   - RAM disponibile

---

**Ultima modifica**: 1 Ottobre 2025
