# 🗄️ Integrazione Server SQL MCP

Guida completa per integrare server MCP esterni come il SQL MCP Server con l'app Realtime Chat.

## 🎯 Panoramica

Ora l'app supporta **l'integrazione completa con server MCP esterni**! Questo permette all'assistente AI di:
- Eseguire query SQL su database
- Accedere a strumenti esterni
- Estendere le capacità dell'AI

## 🔌 Architettura con MCP

```
📱 Android App
    ├─→ RealtimeClient ←→ OpenAI Realtime API
    │       ↓ (tool_call: "execute_sql")
    │       ↓
    └─→ McpBridge ←→ Server MCP SQL
            ↓          (sql-mcp-server.onrender.com)
            ↓
        (query result)
            ↓
        RealtimeClient ─→ OpenAI (ritorna risultato)
            ↓
        Assistente risponde vocalmente con i dati!
```

## 🚀 Setup Server SQL MCP

### Esempio: SQL MCP Server su Render

**URL Server**: `https://sql-mcp-server.onrender.com/mcp-debug`

### Configurazione nell'App

1. **Apri l'app** e vai nelle **Impostazioni** (⚙️)

2. **Aggiungi Server MCP**:
   - Tocca il bottone "+" nella sezione "Server MCP"
   - Compila i campi:
     ```
     Nome: SQL Server
     URL: wss://sql-mcp-server.onrender.com/mcp
     ```
     (Nota: Usa `wss://` per WebSocket sicuro)

3. **Salva Configurazione**

4. **Torna alla schermata principale**

5. L'app si connetterà automaticamente al server MCP!

## 💬 Come Usare

### Scenario 1: Query Database

**Tu (vocalmente)**: *"Quanti utenti ci sono nel database?"*

**Cosa succede**:
1. Realtime API riconosce che serve un tool
2. Chiama il tool `execute_sql` del server MCP
3. Il server esegue: `SELECT COUNT(*) FROM users`
4. Ritorna il risultato
5. **Assistente risponde vocalmente**: *"Nel database ci sono 1,234 utenti"*

### Scenario 2: Dati Complessi

**Tu**: *"Mostrami i 5 prodotti più venduti questo mese"*

**Assistente**: 
1. Usa il tool SQL
2. Esegue query aggregata
3. Ti risponde con i risultati in linguaggio naturale!

### Scenario 3: Con Immagine

**Tu**: 
1. Scatti foto di un grafico
2. Chiedi vocalmente: *"Aggiungi questi dati nel database"*

**Assistente**:
1. Analizza l'immagine
2. Estrae i dati
3. Usa tool SQL per inserirli
4. Conferma l'operazione vocalmente

## 🛠️ Tools Disponibili

Il SQL MCP Server espone questi tools (esempi):

### 1. `execute_sql`
Esegue query SQL read-only

**Parametri**:
```json
{
  "query": "SELECT * FROM users WHERE age > 18"
}
```

### 2. `get_schema`
Ottiene lo schema del database

**Parametri**:
```json
{
  "table_name": "users"
}
```

### 3. `insert_data`
Inserisce nuovi dati

**Parametri**:
```json
{
  "table": "products",
  "data": {"name": "Product A", "price": 29.99}
}
```

## 🔍 Debug e Monitoring

### Verificare Connessione

L'app logga tutti gli eventi MCP:

```kotlin
// In Logcat (Android Studio)
D/McpBridge: Connecting to MCP server: SQL Server at wss://...
D/McpBridge: Connected to MCP server: SQL Server
D/McpBridge: Registered 3 tools from SQL Server
D/MainViewModel: Tool call: execute_sql
D/McpBridge: Executed tool call: execute_sql on server SQL Server
D/MainViewModel: Tool result sent for call abc123
```

### Test della Connessione

1. **Avvia l'app**
2. **Osserva Logcat** per:
   - `Connected to MCP server: SQL Server` ✅
   - `Registered X tools from SQL Server` ✅

3. **Testa con query vocale**:
   - *"Esegui una query SQL: SELECT 1+1"*
   - L'assistente dovrebbe rispondere con il risultato

## 🔧 Creazione Server MCP Custom

### Server SQL Semplice (Node.js)

```javascript
// server.js
const WebSocket = require('ws');
const sqlite3 = require('sqlite3').verbose();

const wss = new WebSocket.Server({ port: 3001 });
const db = new sqlite3.Database(':memory:');

// Crea tabella esempio
db.run(`CREATE TABLE users (
    id INTEGER PRIMARY KEY,
    name TEXT,
    email TEXT
)`);

wss.on('connection', (ws) => {
    console.log('Client connected');
    
    // Invia tools disponibili
    ws.send(JSON.stringify({
        type: 'tools_list',
        tools: [
            {
                name: 'execute_sql',
                description: 'Execute a SQL query',
                parameters: {
                    query: 'string'
                }
            }
        ]
    }));
    
    ws.on('message', (message) => {
        const data = JSON.parse(message);
        
        if (data.type === 'tool_call' && data.name === 'execute_sql') {
            const query = data.parameters.query;
            
            db.all(query, [], (err, rows) => {
                if (err) {
                    ws.send(JSON.stringify({
                        type: 'error',
                        id: data.id,
                        error: err.message
                    }));
                } else {
                    ws.send(JSON.stringify({
                        type: 'tool_result',
                        id: data.id,
                        result: JSON.stringify(rows)
                    }));
                }
            });
        }
    });
});

console.log('SQL MCP Server listening on ws://localhost:3001');
```

**Deploy su Render**:
1. Push su GitHub
2. Vai su render.com
3. New Web Service
4. Connetti repository
5. Deploy!

## 🌐 Server MCP Pubblici

### SQL Server
- **URL**: `wss://sql-mcp-server.onrender.com/mcp`
- **Tools**: execute_sql, get_schema
- **Database**: PostgreSQL/SQLite

### Weather Service
- **URL**: `wss://weather-mcp.herokuapp.com`
- **Tools**: get_weather, get_forecast
- **Data**: OpenWeatherMap API

### File Storage
- **URL**: `wss://files-mcp.vercel.app`
- **Tools**: read_file, write_file, list_files
- **Storage**: S3/Cloud Storage

## 📊 Esempi di Conversazioni

### Query Semplice

```
👤 Tu: "Quanti record ci sono nella tabella users?"

🤖 Assistente:
   [Internamente]
   1. Tool call: execute_sql("SELECT COUNT(*) FROM users")
   2. Riceve: {"count": 42}
   3. Genera risposta
   
   [Vocalmente]
   "Nella tabella users ci sono 42 record"
```

### Query Complessa

```
👤 Tu: "Quali sono gli utenti registrati questa settimana?"

🤖 Assistente:
   [Tool call con query automatica]
   execute_sql("SELECT name, email FROM users 
                WHERE created_at >= date('now', '-7 days')")
   
   [Risposta vocale]
   "Questa settimana si sono registrati 5 utenti: 
    Mario Rossi, Giulia Bianchi, ..."
```

### Multi-Tool

```
👤 Tu: "Controlla il meteo e salvalo nel database"

🤖 Assistente:
   1. Tool: get_weather("Rome")
   2. Tool: execute_sql("INSERT INTO weather ...")
   3. Risponde: "Il meteo a Roma è 22°C, 
                  ho salvato i dati nel database"
```

## ⚠️ Limitazioni e Considerazioni

### Sicurezza
- ⚠️ **Mai esporre database di produzione** direttamente
- ✅ Usa sempre un layer di sicurezza
- ✅ Implementa autenticazione sul server MCP
- ✅ Limita le query permesse (no DROP, DELETE senza WHERE)

### Performance
- Queries complesse potrebbero causare latenza
- L'assistente potrebbe non rispondere immediatamente
- Implementa timeout sui tool calls

### Affidabilità
- Server MCP esterni potrebbero essere offline
- Gestisci gracefully i fallimenti
- L'app continua a funzionare anche senza MCP

## 🎯 Best Practices

### 1. Naming Chiaro
```kotlin
// ✅ GOOD
McpServerConfig(
    name = "Production SQL Server",
    url = "wss://sql.mycompany.com"
)

// ❌ BAD
McpServerConfig(
    name = "Server1",
    url = "wss://abc123.com"
)
```

### 2. Error Handling
Il server MCP dovrebbe sempre ritornare errori chiari:
```json
{
    "type": "error",
    "id": "call_123",
    "error": "Query timeout after 30 seconds"
}
```

### 3. Tool Descriptions
Descrizioni chiare aiutano l'AI a usare i tool correttamente:
```json
{
    "name": "execute_sql",
    "description": "Execute a READ-ONLY SQL query on the database. Use for SELECT statements only. Returns results as JSON array.",
    "parameters": {
        "query": "string - The SQL SELECT query to execute"
    }
}
```

## 🚀 Conclusione

Con l'integrazione MCP completa, la tua app può:
- ✅ Accedere a database esterni
- ✅ Usare API e servizi
- ✅ Estendere infinite le capacità dell'AI
- ✅ Tutto tramite conversazione vocale naturale!

**Il futuro è qui! 🎉**

---

Per domande o problemi, apri un issue su GitHub o consulta la [documentazione MCP](MCP_EXAMPLES.md).
