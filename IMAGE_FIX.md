# 🖼️ Correzione Invio Immagini - Realtime API

## 🐛 Problema

Quando si tentava di inviare un'immagine tramite l'app, si riceveva l'errore:
```
Missing required parameter: 'item.content[0].image_url'
```

## 🔍 Causa

Il formato del messaggio inviato alla Realtime API era **incorretto**. Il codice utilizzava:

```json
{
  "type": "conversation.item.create",
  "item": {
    "type": "message",
    "role": "user",
    "content": [
      {
        "type": "input_image",
        "image": "<base64_string>",     ❌ Campo errato
        "mime_type": "image/jpeg"
      }
    ]
  }
}
```

Ma la Realtime API richiede il formato **data URL**:

```json
{
  "type": "conversation.item.create",
  "item": {
    "type": "message",
    "role": "user",
    "content": [
      {
        "type": "input_image",
        "image_url": "data:image/jpeg;base64,<base64_string>"  ✅ Formato corretto
      }
    ]
  }
}
```

## ✅ Soluzione Implementata

### File Modificato: `RealtimeClient.kt`

**Prima:**
```kotlin
fun sendImage(imageBase64: String, mimeType: String = "image/jpeg") {
    val event = mapOf(
        "type" to "conversation.item.create",
        "item" to mapOf(
            "type" to "message",
            "role" to "user",
            "content" to listOf(
                mapOf(
                    "type" to "input_image",
                    "image" to imageBase64,      // ❌ Campo errato
                    "mime_type" to mimeType
                )
            )
        )
    )
    sendEvent(event)
}
```

**Dopo:**
```kotlin
fun sendImage(imageBase64: String, mimeType: String = "image/jpeg") {
    // Crea data URL nel formato corretto
    val dataUrl = "data:$mimeType;base64,$imageBase64"  // ✅ Data URL
    
    val event = mapOf(
        "type" to "conversation.item.create",
        "item" to mapOf(
            "type" to "message",
            "role" to "user",
            "content" to listOf(
                mapOf(
                    "type" to "input_image",
                    "image_url" to dataUrl       // ✅ Campo corretto
                )
            )
        )
    )
    
    Log.d(TAG, "📸 Sending image: ${mimeType}, size: ${imageBase64.length} chars")
    sendEvent(event)
}
```

## 🎯 Formato Data URL

Il **data URL** è il formato standard per incorporare dati binari (come immagini) in stringhe:

```
data:<mime_type>;base64,<base64_encoded_data>
```

### Esempi:

**JPEG:**
```
data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD...
```

**PNG:**
```
data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAUA...
```

**WebP:**
```
data:image/webp;base64,UklGRiQAAABXRUJQVlA4IBgAAAAw...
```

## 🧪 Test Implementati

Creato `RealtimeImageTest.kt` con 6 test che verificano:

1. ✅ Formato data URL corretto
2. ✅ Struttura data URL
3. ✅ Supporto diversi MIME types
4. ✅ Struttura evento `conversation.item.create`
5. ✅ Gestione base64 con/senza prefisso
6. ✅ Validazione completa del payload

**Risultato:** Tutti i test passano al 100% ✅

## 📱 Come Usare

### 1. Cattura e Invia Immagine

1. **Apri l'app**
2. **Inizia una sessione** (connetti alla Realtime API)
3. **Tocca l'icona della fotocamera** 📷
4. **Scatta una foto**
5. L'immagine viene automaticamente:
   - Convertita in **base64**
   - Formattata come **data URL**
   - Inviata alla Realtime API con il formato corretto

### 2. Risposta dell'AI

L'assistente vocale può ora:
- **Vedere** il contenuto dell'immagine
- **Descrivere** cosa vede
- **Rispondere** a domande sull'immagine
- **Combinare** l'analisi visiva con altre informazioni (audio, testo, MCP tools)

## 💡 Esempi d'Uso

### Scenario 1: Analisi Prodotto
```
👤 [Scatta foto di un prodotto]
🎤 "Cosa vedi in questa immagine?"
🤖 "Vedo una bottiglia di latte. È un prodotto lattiero..."
```

### Scenario 2: Con MCP Tools
```
👤 [Scatta foto di una confezione di yogurt]
🎤 "Aggiungi questo prodotto al magazzino"
🤖 [Analizza immagine] "Vedo uno yogurt alla fragola..."
🤖 [Chiama MCP tool 'inserire_alimento']
🤖 "Ho aggiunto lo yogurt al magazzino!"
```

### Scenario 3: Verifica Scadenze
```
👤 [Scatta foto dell'etichetta]
🎤 "Quando scade questo prodotto?"
🤖 "Dalla foto vedo che la data di scadenza è 15/03/2025"
```

## 🔧 Dettagli Tecnici

### Flusso Completo

1. **Cattura Immagine** (`CameraCapture`)
   ```kotlin
   Bitmap → Base64 String
   ```

2. **Invio Immagine** (`MainViewModel.sendImage()`)
   ```kotlin
   viewModel.sendImage(base64String)
   ```

3. **Formato Data URL** (`RealtimeClient.sendImage()`)
   ```kotlin
   val dataUrl = "data:image/jpeg;base64,$imageBase64"
   ```

4. **Invio alla Realtime API**
   ```json
   {
     "type": "conversation.item.create",
     "item": {
       "type": "message",
       "role": "user",
       "content": [{"type": "input_image", "image_url": "data:..."}]
     }
   }
   ```

5. **Richiesta Risposta**
   ```kotlin
   realtimeClient?.createResponse()
   ```

### Log di Debug

Quando invii un'immagine, vedrai nei log:
```
D/RealtimeClient: 📸 Sending image: image/jpeg, size: 45678 chars
D/RealtimeClient: 📤 Sending event type: conversation.item.create
```

## 🎉 Risultato

✅ **Le immagini ora vengono inviate correttamente**
✅ **L'AI può vedere e analizzare le immagini**
✅ **Funziona in combinazione con voce, testo e MCP tools**
✅ **Supporta JPEG, PNG, WebP**
✅ **Test completi per evitare regressioni**

## 📚 Riferimenti

- [OpenAI Realtime API Documentation](https://platform.openai.com/docs/guides/realtime)
- [Data URL Specification (RFC 2397)](https://datatracker.ietf.org/doc/html/rfc2397)
- [Base64 Encoding](https://en.wikipedia.org/wiki/Base64)

---

**Status:** ✅ **PROBLEMA RISOLTO**  
**Data:** 2025-10-03  
**Build:** Successful  
**Test:** 6/6 passati
