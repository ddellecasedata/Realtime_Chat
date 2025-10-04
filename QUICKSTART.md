# 🚀 Quick Start Guide - Realtime Chat

Guida rapida per iniziare a utilizzare l'app in pochi minuti.

## ⚡ Setup Veloce

### 1. Prerequisiti
- Android Studio installato
- Dispositivo Android o emulatore con Android 7.0+ (API 24+)
- Account OpenAI con accesso alle Realtime API

### 2. Installazione

```bash
# Clona il repository
git clone <repository-url>
cd Realtime_Chat

# Apri in Android Studio
# File > Open > Seleziona la cartella Realtime_Chat
```

### 3. Ottieni l'API Key

1. Vai su https://platform.openai.com/api-keys
2. Crea una nuova chiave API
3. Copia la chiave (la userai nell'app)

### 4. Avvia l'App

1. Connetti il dispositivo o avvia l'emulatore
2. Clicca su **Run** (▶️) in Android Studio
3. Attendi che l'app si installi

## 📱 Primo Utilizzo

### Configurazione Iniziale

1. **Apri le Impostazioni**
   - All'avvio vedrai un messaggio "Configurazione Richiesta"
   - Tocca "Vai alle Impostazioni" oppure l'icona ⚙️ in alto a destra

2. **Inserisci l'API Key**
   - Incolla la tua OpenAI API Key nel campo "API Key"
   - L'API Key sarà nascosta per sicurezza (puoi toccare "Mostra" per vederla)

3. **Aggiungi Server MCP (Opzionale)**
   - Tocca il bottone "+" nella sezione "Server MCP"
   - Inserisci nome e URL del server
   - Esempio: `ws://localhost:3000`
   - Tocca "Aggiungi"

4. **Salva**
   - Tocca "Salva Configurazione"
   - Torna alla schermata principale

### Utilizzo Base

#### 🎤 Chat Vocale

1. Assicurati che lo stato sia "✅ Connesso"
2. Tocca il bottone **"Avvia Audio"** (microfono)
3. Parla con l'assistente
4. L'assistente risponderà vocalmente in tempo reale
5. Tocca **"Stop Audio"** per fermare la registrazione

#### 📷 Invia Immagini

1. Tocca il bottone **"Fotocamera"**
2. Concedi i permessi se richiesto
3. Scatta una foto
4. L'immagine verrà automaticamente inviata all'assistente
5. L'assistente analizzerà l'immagine e risponderà

## 🔑 Permessi Necessari

L'app richiederà questi permessi:

- ✅ **Microfono**: Per la chat vocale
- ✅ **Fotocamera**: Per acquisire immagini
- ✅ **Internet**: Per comunicare con OpenAI

Tutti i permessi sono richiesti solo quando necessari (runtime permissions).

## 💡 Tips & Tricks

### Conversazione Fluida
- L'assistente rileva automaticamente quando hai finito di parlare
- Non è necessario toccare "Stop" ogni volta
- Puoi interrompere l'assistente parlando sopra

### Gestione Immagini
- Puoi inviare multiple immagini
- Le immagini rimangono nel contesto della conversazione
- Tocca "Cancella" per rimuovere le immagini accumulate

### Ottimizzare le Performance
- Usa una connessione WiFi stabile per la migliore esperienza
- Assicurati che il microfono non sia ostruito
- Parla chiaramente e a distanza normale dal dispositivo

## 🐛 Troubleshooting Rapido

### L'app non si connette
```
❌ Problema: "Errore durante l'inizializzazione"
✅ Soluzione: 
   1. Verifica l'API Key nelle impostazioni
   2. Controlla la connessione Internet
   3. Assicurati di avere crediti sufficienti su OpenAI
```

### Audio non funziona
```
❌ Problema: "Impossibile avviare la registrazione audio"
✅ Soluzione:
   1. Concedi il permesso microfono
   2. Chiudi altre app che usano il microfono
   3. Riavvia l'app
```

### Fotocamera non si apre
```
❌ Problema: "Errore inizializzazione fotocamera"
✅ Soluzione:
   1. Concedi il permesso fotocamera
   2. Chiudi altre app che usano la fotocamera
   3. Riavvia il dispositivo se necessario
```

## 🎯 Esempi di Utilizzo

### Esempio 1: Assistente Vocale
```
Tu: "Ciao, mi puoi aiutare con una ricetta?"
Assistente: "Certo! Che tipo di ricetta stai cercando?"
Tu: "Vorrei fare una torta al cioccolato"
Assistente: "Perfetto! Ti serve una ricetta semplice o elaborata?"
```

### Esempio 2: Analisi Immagini
```
1. Tocca "Fotocamera"
2. Scatta foto di un documento
3. Assistente: "Vedo un documento con testo. Vuoi che te lo legga?"
4. Tu: "Sì, per favore"
5. Assistente: [Legge il contenuto del documento]
```

### Esempio 3: Multimodale
```
1. Tocca "Fotocamera" e scatta foto di un piatto
2. Tocca "Avvia Audio"
3. Tu: "Cosa vedi in questa immagine? È salutare?"
4. Assistente: [Analizza l'immagine e fornisce risposta vocale]
```

## 📊 Stati dell'App

| Icona | Stato | Descrizione |
|-------|-------|-------------|
| ⏸️ | In attesa | L'app è pronta ma non connessa |
| 🔄 | Connessione in corso... | Sta stabilendo la connessione |
| ✅ | Connesso | Pronto per l'uso |
| ❌ | Errore | C'è un problema (leggi il messaggio) |

## 🔐 Privacy & Sicurezza

- ✅ L'API Key è memorizzata in modo sicuro sul dispositivo
- ✅ I dati audio e immagini sono inviati direttamente a OpenAI
- ✅ Nessun dato viene memorizzato sui nostri server
- ✅ Puoi cancellare l'API Key in qualsiasi momento dalle impostazioni

## 📞 Supporto

Per problemi o domande:
- Consulta il README.md per documentazione completa
- Apri un issue su GitHub
- Controlla la documentazione OpenAI: https://platform.openai.com/docs

## 🎉 Pronto!

Ora sei pronto per utilizzare Realtime Chat! 

**Buon divertimento con il tuo assistente vocale AI! 🚀**
