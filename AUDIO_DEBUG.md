# 🎤 Audio Debug Guide - Realtime Chat

## 🔍 Come Debuggare il Problema Audio

### 1️⃣ Visualizza i Log in Tempo Reale

```bash
./VIEW_LOGS.sh
```

### 2️⃣ Sequenza Normale di Eventi

Quando **parli** e **rilasci**, dovresti vedere:

```
✅ SESSION READY - FULL-DUPLEX MODE
🎤 Audio recording started - streaming to API...
📤 Sending audio chunk #1 (1234 chars)
📤 Sending audio chunk #2 (1234 chars)
📤 Sending audio chunk #3 (1234 chars)
...
🛑 Stopping recording - committing audio buffer...
📤 Committing audio buffer to API...
📤 Sending event type: input_audio_buffer.commit
🤖 Requesting response from API...
📤 Sending event type: response.create
✅ Audio committed - waiting for response...

# Se l'API risponde:
📨 Event received: ResponseAudioDelta
🔊 Audio delta received: 5678 bytes
📨 Event received: ResponseAudioDone
✅ Audio response completed
```

### 3️⃣ Problemi Comuni

#### ❌ Problema: Nessun chunk audio inviato
**Causa**: Microfono non registra
**Soluzione**: 
- Verifica permessi audio
- Controlla log: `AudioManager: AudioRecord not initialized`

#### ❌ Problema: Chunks inviati ma nessun commit
**Causa**: `stopAudioRecording()` non chiamato
**Soluzione**: 
- Verifica che rilasci il pulsante
- Cerca log: `🛑 Stopping recording`

#### ❌ Problema: Commit OK ma nessuna risposta
**Causa**: API non connessa o API key invalida
**Soluzione**:
```bash
# Verifica connessione WebSocket
adb logcat | grep "Connected to Realtime API"

# Se vedi:
✅ Connected to Realtime API  → OK
❌ Disconnected               → Problema connessione

# Controlla API key in Impostazioni
```

#### ❌ Problema: Nessun evento ricevuto
**Causa**: WebSocket non riceve messaggi
**Soluzione**:
- Verifica API key corretta
- Controlla internet/firewall
- Log: `📨 Event received:` dovrebbe apparire

### 4️⃣ Test Rapido

1. **Apri app**
2. **Esegui**:
   ```bash
   ./VIEW_LOGS.sh
   ```
3. **Premi "Parla"** (guarda log)
4. **Di' "Ciao"** (guarda chunks)
5. **Rilascia bottone** (guarda commit)
6. **Aspetta 2-3 secondi** (guarda eventi risposta)

### 5️⃣ Log Diagnostici Completi

Per salvare tutti i log per analisi:

```bash
adb logcat > app_logs.txt
# Usa l'app per 30 secondi
# CTRL+C per fermare
# Invia app_logs.txt per analisi
```

### 6️⃣ Verifica API Key

Se hai dubbi sull'API key:

```bash
# Test manuale
curl https://api.openai.com/v1/models \
  -H "Authorization: Bearer LA_TUA_API_KEY"

# Se risponde con lista modelli → API key valida
# Se risponde 401 → API key invalida
```

### 7️⃣ Reset Completo

Se nulla funziona:

1. **Disinstalla app**
   ```bash
   adb uninstall com.things5.realtimechat
   ```

2. **Reinstalla**
   ```bash
   ./gradlew installDebug
   ```

3. **Riconfigura** API key e MCP server

4. **Test di nuovo**

## 📊 Metriche Normali

- **Chunk audio**: ~1200-1500 chars ogni 100ms
- **Tempo risposta**: 1-3 secondi dopo commit
- **Audio delta**: ~3000-8000 bytes per chunk
- **Latenza playback**: <200ms

## 🆘 Se Ancora Non Funziona

Esegui e inviami l'output:

```bash
./VIEW_LOGS.sh > debug_output.txt
# Usa l'app
# CTRL+C
# Invia debug_output.txt
```
