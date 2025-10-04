# ❓ Frequently Asked Questions (FAQ)

Risposte alle domande più comuni su Realtime Chat.

## 📱 Generale

### Cos'è Realtime Chat?

Realtime Chat è un'app Android che integra le Realtime API di OpenAI per fornire un'esperienza di chat vocale in tempo reale. Permette conversazioni naturali con un assistente AI e supporta l'invio di immagini per analisi multimodale.

### È gratuita?

L'app è open source e gratuita. Tuttavia, hai bisogno di un account OpenAI e crediti per utilizzare le Realtime API. Consulta i [prezzi di OpenAI](https://openai.com/pricing) per i dettagli.

### Su quali dispositivi funziona?

L'app funziona su dispositivi Android 7.0 (API 24) o successivi. Richiede microfono e fotocamera per la piena funzionalità.

### I miei dati sono sicuri?

Sì. L'API Key è memorizzata in modo sicuro sul dispositivo usando DataStore. Audio e immagini sono inviati direttamente a OpenAI tramite connessione criptata e non sono salvati permanentemente sul dispositivo.

## 🔑 Setup & Configurazione

### Come ottengo un'API Key di OpenAI?

1. Vai su [platform.openai.com](https://platform.openai.com)
2. Crea un account o accedi
3. Naviga su "API Keys"
4. Crea una nuova chiave segreta
5. Copia la chiave (non sarà più visibile dopo)

### Dove inserisco l'API Key nell'app?

1. Apri l'app
2. Tocca l'icona ⚙️ (Impostazioni) in alto a destra
3. Incolla la chiave nel campo "API Key"
4. Tocca "Salva Configurazione"

### Posso cambiare l'API Key dopo averla salvata?

Sì, vai nelle Impostazioni in qualsiasi momento e inserisci una nuova chiave.

### Cosa succede se perdo l'API Key?

L'API Key è memorizzata solo sul tuo dispositivo. Se disinstalli l'app o perdi il dispositivo, dovrai inserirla nuovamente. Per sicurezza, conserva l'API Key in un password manager.

## 🎤 Audio & Voice

### L'audio non funziona, cosa faccio?

**Checklist:**
- ✅ Hai concesso il permesso microfono?
- ✅ Il microfono funziona in altre app?
- ✅ C'è troppo rumore ambientale?
- ✅ L'app è connessa (stato "✅ Connesso")?

**Soluzioni:**
1. Vai in Impostazioni Android > App > Realtime Chat > Permessi
2. Verifica che "Microfono" sia abilitato
3. Riavvia l'app
4. Prova con cuffie/auricolari

### L'assistente non mi sente bene

**Suggerimenti:**
- Parla a distanza normale dal microfono (15-30 cm)
- Riduci il rumore di fondo
- Non coprire il microfono del dispositivo
- Parla chiaramente a volume normale
- Usa cuffie con microfono integrato

### L'assistente continua a interrompere mentre parlo

Il sistema usa Voice Activity Detection (VAD) per rilevare quando hai finito di parlare. Se interrompe troppo presto:

- Parla senza pause lunghe
- Riduci il rumore ambientale
- La sensibilità migliorerà con l'uso

### Posso usare cuffie Bluetooth?

Sì, le cuffie Bluetooth sono supportate. Assicurati che siano connesse prima di avviare l'app.

### Quanto dura la batteria durante l'uso?

L'uso continuo della chat vocale consuma batteria. Aspettati circa:
- 2-3 ore di conversazione continua
- 4-5 ore con uso intermittente

## 📷 Fotocamera & Immagini

### La fotocamera non si apre

**Soluzioni:**
1. Verifica permessi: Impostazioni > App > Realtime Chat > Permessi > Fotocamera
2. Chiudi altre app che usano la fotocamera
3. Riavvia il dispositivo
4. Verifica che la fotocamera funzioni in altre app

### Posso inviare immagini dalla galleria?

Attualmente solo foto scattate in-app sono supportate. Il supporto galleria è pianificato per una versione futura.

### Quante immagini posso inviare?

Puoi inviare multiple immagini nella stessa conversazione. Sono tutte parte del contesto.

### Le immagini vengono salvate?

No, le immagini non sono salvate permanentemente. Sono conservate in memoria solo durante la sessione corrente e possono essere cancellate toccando "Cancella".

### Che qualità hanno le immagini inviate?

Le immagini sono compresse in JPEG al 90% per ottimizzare la trasmissione mantenendo buona qualità.

## 🌐 Connessione & Network

### L'app dice "Errore durante l'inizializzazione"

**Cause comuni:**
- API Key non valida o scaduta
- Nessuna connessione Internet
- Crediti OpenAI esauriti
- Server OpenAI temporaneamente non disponibile

**Soluzioni:**
1. Verifica l'API Key nelle impostazioni
2. Controlla la connessione Internet
3. Verifica crediti su [platform.openai.com](https://platform.openai.com)
4. Riprova dopo qualche minuto

### Quanto traffico dati consuma?

Circa:
- 1-2 MB/min per audio bidirezionale
- 0.5-1 MB per immagine inviata
- Totale: ~5-10 MB per sessione di 10 minuti

Usa WiFi quando possibile per risparmiare dati mobili.

### Funziona offline?

No, è richiesta connessione Internet per comunicare con le API di OpenAI. La configurazione può essere modificata offline ma non puoi usare le funzionalità di chat.

### Posso usare VPN?

Sì, VPN è supportata. Assicurati che non blocchi il traffico WebSocket.

## 🔧 Server MCP

### Cosa sono i server MCP?

Model Context Protocol (MCP) permette all'assistente di accedere a strumenti esterni come database, API, file system, etc. Vedi [MCP_EXAMPLES.md](MCP_EXAMPLES.md) per dettagli.

### Devo configurare server MCP?

No, sono opzionali. L'app funziona perfettamente senza. Aggiungili solo se vuoi estendere le capacità dell'assistente.

### Come creo un server MCP?

Consulta [MCP_EXAMPLES.md](MCP_EXAMPLES.md) per esempi e guide complete in Node.js e Python.

### Il server MCP locale non funziona

**Per test su dispositivo fisico:**
1. Il server deve essere accessibile sulla rete
2. Usa l'IP locale del PC invece di `localhost`
   - Esempio: `ws://192.168.1.100:3001`
3. Verifica che il firewall permetta la connessione
4. Il dispositivo deve essere sulla stessa rete WiFi

**Esempio:**
```bash
# Sul PC trova l'IP
ifconfig | grep "inet "  # Mac/Linux
ipconfig                  # Windows

# Nell'app usa
URL: ws://192.168.1.100:3001
```

## ⚙️ Prestazioni & Ottimizzazione

### L'app è lenta

**Suggerimenti:**
- Chiudi app in background
- Libera spazio di archiviazione
- Usa WiFi invece di dati mobili
- Riavvia il dispositivo

### C'è latenza nella risposta vocale

La latenza dipende da:
- Velocità della connessione Internet
- Carico dei server OpenAI
- Prestazioni del dispositivo

**Per ridurre latenza:**
- Usa WiFi veloce e stabile
- Evita ore di picco
- Chiudi app non necessarie

### L'app si blocca o crasha

1. **Aggiorna l'app** all'ultima versione
2. **Pulisci cache**: Impostazioni > App > Realtime Chat > Cancella cache
3. **Disinstalla e reinstalla** l'app
4. **Segnala il bug** con i log

## 💰 Costi & Utilizzo

### Quanto costa usare l'app?

L'app è gratuita, ma OpenAI addebita per l'uso delle API. Costi approssimativi:
- ~$0.06 per minuto di audio input
- ~$0.24 per minuto di audio output
- Varia in base al modello usato

Consulta [OpenAI Pricing](https://openai.com/pricing) per i prezzi aggiornati.

### Come monitoro i costi?

Vai su [platform.openai.com/usage](https://platform.openai.com/usage) per vedere l'utilizzo in tempo reale.

### Posso impostare un limite di spesa?

Sì, configura limiti di spesa nel [dashboard OpenAI](https://platform.openai.com/account/billing/limits).

## 🔒 Privacy & Sicurezza

### OpenAI conserva le mie conversazioni?

Secondo la politica di OpenAI, le conversazioni potrebbero essere conservate per migliorare i modelli, a meno che tu non abbia configurato diversamente nelle impostazioni del tuo account OpenAI.

### Posso usare l'app in modalità privata?

L'app non salva conversazioni localmente. Per privacy completa, configura il tuo account OpenAI per opt-out dalla conservazione dati.

### L'API Key è sicura?

Sì, è memorizzata usando DataStore con crittografia a livello sistema Android. Non è mai loggata o esposta.

## 🆘 Supporto & Aiuto

### Ho trovato un bug, cosa faccio?

1. Controlla se è già segnalato negli [Issues GitHub](https://github.com/yourusername/Realtime_Chat/issues)
2. Se no, apri un nuovo issue con:
   - Descrizione del problema
   - Steps per riprodurlo
   - Screenshot/log se possibile
   - Informazioni dispositivo

### Ho un'idea per una nuova feature

Fantastico! Apri un [Discussion](https://github.com/yourusername/Realtime_Chat/discussions) o un issue con label "enhancement".

### Posso contribuire al codice?

Assolutamente! Leggi [CONTRIBUTING.md](CONTRIBUTING.md) per le linee guida.

### Dove trovo più documentazione?

- **README.md**: Overview completo
- **QUICKSTART.md**: Guida rapida
- **ARCHITECTURE.md**: Dettagli tecnici
- **MCP_EXAMPLES.md**: Guida MCP
- **CONTRIBUTING.md**: Come contribuire

## 🔄 Aggiornamenti

### Come aggiorno l'app?

Se installata da source:
1. `git pull` per ottenere l'ultima versione
2. Ricompila in Android Studio

Se disponibile su store, l'aggiornamento sarà automatico.

### Dove vedo le novità delle nuove versioni?

Consulta [CHANGELOG.md](CHANGELOG.md) per tutte le modifiche.

## 🌍 Lingue & Localizzazione

### In che lingua risponde l'assistente?

L'assistente risponde nella lingua in cui parli. Supporta le principali lingue incluso italiano, inglese, spagnolo, francese, tedesco, etc.

### L'app è disponibile in altre lingue?

Attualmente l'interfaccia è in italiano. Contributi per traduzioni sono benvenuti!

## 📊 Statistiche & Analytics

### L'app raccoglie dati di utilizzo?

No, l'app non raccoglie analytics o telemetria. Tutto il tracking è disabilitato.

### Posso vedere statistiche del mio utilizzo?

Le statistiche di utilizzo API sono disponibili nel dashboard OpenAI.

---

## ❓ La tua domanda non è qui?

- 💬 Apri una [Discussion](https://github.com/yourusername/Realtime_Chat/discussions)
- 🐛 [Segnala un Issue](https://github.com/yourusername/Realtime_Chat/issues)
- 📧 Contatta i maintainer

**Contribuisci a questa FAQ!** Se hai una domanda comune non elencata, aprila come issue o PR.
