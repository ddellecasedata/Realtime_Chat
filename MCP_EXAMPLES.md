# 🔧 Esempi di Configurazione MCP Server

Questa guida fornisce esempi pratici di configurazione per server MCP (Model Context Protocol).

## 📖 Cos'è MCP?

Model Context Protocol è un protocollo standardizzato che permette agli assistenti AI di accedere a strumenti e dati esterni. I server MCP espongono funzionalità che l'assistente può utilizzare durante la conversazione.

## 🛠️ Server MCP di Esempio

### 1. File System Server

Permette all'assistente di leggere e scrivere file.

**Configurazione nell'app:**
```
Nome: File System
URL: ws://localhost:3001
```

**Capacità:**
- Leggere file di testo
- Scrivere nuovi file
- Listare directory
- Cercare file

**Esempio d'uso:**
```
Tu: "Crea un file chiamato note.txt con il contenuto 'Hello World'"
Assistente: [Usa il tool file_write del server MCP]
Assistente: "Ho creato il file note.txt con il contenuto richiesto"
```

### 2. Database Server

Permette query a database.

**Configurazione nell'app:**
```
Nome: Database
URL: ws://localhost:3002
```

**Capacità:**
- Eseguire query SQL
- Ottenere schema database
- Inserire/aggiornare record

**Esempio d'uso:**
```
Tu: "Quanti utenti abbiamo nel database?"
Assistente: [Esegue SELECT COUNT(*) FROM users]
Assistente: "Ci sono 1,234 utenti nel database"
```

### 3. Web Search Server

Permette ricerche web.

**Configurazione nell'app:**
```
Nome: Web Search
URL: ws://localhost:3003
```

**Capacità:**
- Ricerca su Google/Bing
- Lettura contenuti web
- Estrazione informazioni

**Esempio d'uso:**
```
Tu: "Qual è il meteo a Roma oggi?"
Assistente: [Cerca su web tramite MCP]
Assistente: "A Roma oggi ci sono 22°C con cielo sereno"
```

### 4. Calendar Server

Gestione calendario e appuntamenti.

**Configurazione nell'app:**
```
Nome: Calendar
URL: ws://localhost:3004
```

**Capacità:**
- Creare eventi
- Leggere agenda
- Modificare appuntamenti
- Inviare reminder

**Esempio d'uso:**
```
Tu: "Aggiungi un meeting domani alle 15:00"
Assistente: [Usa calendar_create_event]
Assistente: "Ho aggiunto il meeting al tuo calendario per domani alle 15:00"
```

### 5. GitHub Server

Interazione con repository GitHub.

**Configurazione nell'app:**
```
Nome: GitHub
URL: ws://localhost:3005
```

**Capacità:**
- Leggere repository
- Creare issue
- Fare commit
- Review PR

**Esempio d'uso:**
```
Tu: "Quante issue aperte ha il progetto?"
Assistente: [Usa github_list_issues]
Assistente: "Il progetto ha 15 issue aperte"
```

## 🚀 Come Creare un Server MCP Personalizzato

### Esempio Base (Node.js)

```javascript
// server.js
const WebSocket = require('ws');

const wss = new WebSocket.Server({ port: 3001 });

wss.on('connection', (ws) => {
    console.log('Client connected');
    
    // Invia lista strumenti disponibili
    ws.send(JSON.stringify({
        type: 'tools',
        tools: [
            {
                name: 'get_current_time',
                description: 'Get the current time',
                parameters: {}
            }
        ]
    }));
    
    ws.on('message', (message) => {
        const data = JSON.parse(message);
        
        if (data.type === 'tool_call' && data.name === 'get_current_time') {
            // Esegui la funzione
            const result = new Date().toISOString();
            
            // Rispondi
            ws.send(JSON.stringify({
                type: 'tool_result',
                id: data.id,
                result: result
            }));
        }
    });
    
    ws.on('close', () => {
        console.log('Client disconnected');
    });
});

console.log('MCP Server listening on ws://localhost:3001');
```

**Esecuzione:**
```bash
npm install ws
node server.js
```

### Esempio con Python

```python
# server.py
import asyncio
import websockets
import json
from datetime import datetime

async def handler(websocket):
    # Invia strumenti disponibili
    await websocket.send(json.dumps({
        'type': 'tools',
        'tools': [
            {
                'name': 'get_weather',
                'description': 'Get weather for a city',
                'parameters': {
                    'city': 'string'
                }
            }
        ]
    }))
    
    async for message in websocket:
        data = json.loads(message)
        
        if data['type'] == 'tool_call' and data['name'] == 'get_weather':
            city = data['parameters']['city']
            
            # Simulazione chiamata API meteo
            result = f"Weather in {city}: Sunny, 22°C"
            
            await websocket.send(json.dumps({
                'type': 'tool_result',
                'id': data['id'],
                'result': result
            }))

async def main():
    async with websockets.serve(handler, "localhost", 3001):
        print("MCP Server listening on ws://localhost:3001")
        await asyncio.Future()

if __name__ == "__main__":
    asyncio.run(main())
```

**Esecuzione:**
```bash
pip install websockets
python server.py
```

## 🔗 Protocollo MCP

### Formato Messaggi

#### 1. Registrazione Strumenti
```json
{
    "type": "tools",
    "tools": [
        {
            "name": "tool_name",
            "description": "Tool description",
            "parameters": {
                "param1": "type",
                "param2": "type"
            }
        }
    ]
}
```

#### 2. Chiamata Strumento
```json
{
    "type": "tool_call",
    "id": "unique_call_id",
    "name": "tool_name",
    "parameters": {
        "param1": "value1",
        "param2": "value2"
    }
}
```

#### 3. Risultato Strumento
```json
{
    "type": "tool_result",
    "id": "unique_call_id",
    "result": "tool result data"
}
```

#### 4. Errore
```json
{
    "type": "error",
    "id": "unique_call_id",
    "error": "Error message"
}
```

## 🌐 Configurazione Network

### Server Locale (Development)

Per testare su dispositivo fisico con server locale sul PC:

1. **Trova l'IP locale del PC:**
   ```bash
   # Mac/Linux
   ifconfig | grep "inet "
   
   # Windows
   ipconfig
   ```

2. **Usa l'IP invece di localhost:**
   ```
   Nome: My Server
   URL: ws://192.168.1.100:3001
   ```

3. **Assicurati che firewall permetta la connessione:**
   ```bash
   # Mac
   System Preferences > Security > Firewall
   
   # Linux
   sudo ufw allow 3001
   ```

### Server Cloud (Production)

Per server in produzione:

```
Nome: Production Server
URL: wss://mcp.example.com
```

**Note:**
- Usa `wss://` (WebSocket Secure) per HTTPS
- Configura SSL/TLS sul server
- Implementa autenticazione se necessario

## 🛡️ Best Practices

### Sicurezza

1. **Autenticazione**
   ```json
   {
       "type": "auth",
       "token": "your_auth_token"
   }
   ```

2. **Validazione Input**
   - Valida sempre i parametri ricevuti
   - Sanitizza gli input
   - Limita le operazioni permesse

3. **Rate Limiting**
   - Limita il numero di chiamate per secondo
   - Implementa timeout

### Performance

1. **Caching**
   - Cachea risultati frequenti
   - Usa TTL appropriate

2. **Async Operations**
   - Esegui operazioni lunghe in background
   - Ritorna status immediato

3. **Error Handling**
   - Gestisci tutti gli errori
   - Ritorna messaggi chiari

## 📚 Risorse

### Documentazione
- [Model Context Protocol Spec](https://modelcontextprotocol.io)
- [OpenAI Agents SDK](https://github.com/openai/openai-agents-js)
- [WebSocket API](https://developer.mozilla.org/en-US/docs/Web/API/WebSocket)

### Repository Esempio
- [MCP Server Examples](https://github.com/openai/mcp-examples)
- [Community MCP Servers](https://github.com/topics/mcp-server)

### Tools & Libraries
- **Node.js**: ws, socket.io
- **Python**: websockets, asyncio
- **Go**: gorilla/websocket
- **Rust**: tokio-tungstenite

## 💡 Idee per Server Personalizzati

1. **Smart Home Controller**: Controlla dispositivi IoT
2. **Email Manager**: Leggi/invia email
3. **Task Manager**: Gestione TODO e progetti
4. **Analytics Server**: Query analytics in tempo reale
5. **Translation Service**: Traduzioni in tempo reale
6. **Code Executor**: Esegui codice in sandbox
7. **Image Generator**: Genera immagini con Stable Diffusion
8. **RSS Reader**: Leggi feed RSS
9. **Weather Service**: Dati meteo personalizzati
10. **Notification Server**: Invia notifiche push

## 🤝 Contribuire

Hai creato un server MCP interessante? Condividilo!

1. Crea una PR con il tuo esempio
2. Documenta l'API
3. Fornisci istruzioni di setup
4. Aggiungi test

---

**Happy MCP Hacking! 🚀**
