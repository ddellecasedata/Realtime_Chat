# Fix: Corretta Gestione dei Tipi dei Parametri MCP

## Problema
I parametri passati ai tool MCP venivano serializzati in modo errato, convertendo i tipi primitivi (boolean, number) in stringhe. Ad esempio:
- `"is_connected": "true"` invece di `"is_connected": true`
- Questo causava errori di tipo `invalid_type` dal server MCP

## Causa
Quando i parametri venivano passati come `Map<String, Any>` e poi serializzati con Gson, i tipi non venivano preservati correttamente durante la conversione JSON.

## Soluzione Implementata

### File Modificati
- `app/src/main/java/com/things5/realtimechat/network/McpBridge.kt`

### Modifiche

#### 1. Metodo `executeToolCallHttp` (linee 648-690)
Aggiunta conversione esplicita dei parametri in `JsonObject` per preservare i tipi:

```kotlin
// Converti i parametri in un JsonObject per preservare i tipi corretti
val argumentsJson = JsonObject()
parameters.forEach { (key, value) ->
    when (value) {
        is Boolean -> argumentsJson.addProperty(key, value)
        is Number -> argumentsJson.addProperty(key, value)
        is String -> argumentsJson.addProperty(key, value)
        else -> {
            // Per oggetti complessi, prova a parsarli come JSON
            try {
                val jsonElement = gson.toJsonTree(value)
                argumentsJson.add(key, jsonElement)
            } catch (e: Exception) {
                // Fallback: converti in stringa
                argumentsJson.addProperty(key, value.toString())
            }
        }
    }
}

// Crea richiesta JSON-RPC 2.0 usando JsonObject
val jsonRpcRequest = JsonObject().apply {
    addProperty("jsonrpc", "2.0")
    addProperty("id", requestId)
    addProperty("method", "tools/call")
    add("params", JsonObject().apply {
        addProperty("name", toolName)
        add("arguments", argumentsJson)
    })
}
```

#### 2. Metodo `executeToolCallWebSocket` (linee 625-665)
Stessa logica applicata per WebSocket per coerenza:

```kotlin
// Converti i parametri in un JsonObject per preservare i tipi corretti
val parametersJson = JsonObject()
parameters.forEach { (key, value) ->
    when (value) {
        is Boolean -> parametersJson.addProperty(key, value)
        is Number -> parametersJson.addProperty(key, value)
        is String -> parametersJson.addProperty(key, value)
        else -> {
            try {
                val jsonElement = gson.toJsonTree(value)
                parametersJson.add(key, jsonElement)
            } catch (e: Exception) {
                parametersJson.addProperty(key, value.toString())
            }
        }
    }
}

val message = JsonObject().apply {
    addProperty("type", "tool_call")
    addProperty("id", callId)
    addProperty("name", toolName)
    add("parameters", parametersJson)
}
```

#### 3. Logging Migliorato
Aggiunto log per debug dei tipi dei parametri (linea 730):
```kotlin
Log.d(TAG, "Parameters types: ${parameters.map { "${it.key}:${it.value::class.simpleName}" }}")
```

### Test Implementati
- `app/src/test/java/com/things5/realtimechat/McpParameterTypeTest.kt`

4 test che verificano:
1. ✅ Preservazione dei parametri boolean
2. ✅ Preservazione dei parametri numerici (int e double)
3. ✅ Preservazione di parametri misti (string, number, boolean)
4. ✅ Gestione corretta dei parametri provenienti da OpenAI Realtime API

## Risultati Test
```
✅ 4 test eseguiti
✅ 0 falliti
✅ 0 errori
```

### Output dei Test
```json
// Esempio di JSON corretto generato:
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "update_server_status",
    "arguments": {
      "is_connected": true,        // ✅ boolean, non "true"
      "name": "test_server",        // ✅ string
      "port": 8080,                 // ✅ number
      "timeout": 30.5               // ✅ double
    }
  }
}
```

## Verifica
Per testare in produzione:
1. Avvia l'app
2. Connetti a un server MCP
3. Chiedi all'assistente di chiamare un tool con parametri boolean/number
4. Verifica nei log che i parametri siano del tipo corretto:
   ```
   🔧 Executing tool call via HTTP (JSON-RPC): tool_name on server server_name
   Parameters types: [is_connected:Boolean, port:Int]
   Request: {"jsonrpc":"2.0",...,"arguments":{"is_connected":true,"port":8080}}
   ```

## Note
- La soluzione gestisce correttamente tutti i tipi primitivi (Boolean, Number, String)
- Per oggetti complessi, utilizza `gson.toJsonTree()` per preservare la struttura
- Fallback a stringa solo per tipi non riconosciuti
- Applicata sia per HTTP (JSON-RPC) che WebSocket per coerenza
