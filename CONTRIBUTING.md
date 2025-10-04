# 🤝 Contributing to Realtime Chat

Grazie per il tuo interesse nel contribuire a Realtime Chat! Questo documento fornisce linee guida per contribuire al progetto.

## 📋 Indice

- [Code of Conduct](#code-of-conduct)
- [Come Contribuire](#come-contribuire)
- [Setup Ambiente di Sviluppo](#setup-ambiente-di-sviluppo)
- [Standard di Codice](#standard-di-codice)
- [Processo di Pull Request](#processo-di-pull-request)
- [Testing](#testing)
- [Documentazione](#documentazione)

## 🤝 Code of Conduct

### I Nostri Impegni

- Essere rispettosi e inclusivi
- Accettare critiche costruttive
- Focalizzarsi su ciò che è meglio per la community
- Mostrare empatia verso gli altri membri

### Comportamenti Non Accettabili

- Linguaggio o immagini offensive
- Trolling o commenti provocatori
- Attacchi personali o politici
- Molestie pubbliche o private
- Pubblicare informazioni private altrui

## 🚀 Come Contribuire

### Segnalare Bug

Prima di aprire un issue per un bug:

1. **Controlla se il bug è già stato segnalato**
   - Cerca negli [issues esistenti](https://github.com/yourusername/Realtime_Chat/issues)

2. **Raccogli informazioni**
   - Versione Android
   - Modello dispositivo
   - Versione dell'app
   - Log degli errori

3. **Apri un nuovo issue** con:
   ```markdown
   **Descrizione del Bug**
   Una descrizione chiara del problema.
   
   **Steps to Reproduce**
   1. Vai a '...'
   2. Clicca su '...'
   3. Vedi errore
   
   **Comportamento Atteso**
   Cosa dovrebbe succedere normalmente.
   
   **Screenshots**
   Se applicabile, aggiungi screenshot.
   
   **Ambiente**
   - Dispositivo: [es. Samsung Galaxy S21]
   - OS: [es. Android 13]
   - Versione App: [es. 1.0.0]
   
   **Log**
   ```
   Paste dei log rilevanti
   ```
   ```

### Proporre Nuove Funzionalità

1. **Apri un issue** con label "enhancement"
2. **Descrivi la funzionalità** in dettaglio
3. **Spiega il caso d'uso**
4. **Discuti con la community** prima di implementare

### Migliorare la Documentazione

La documentazione è importante! Contributi benvenuti per:
- Correzioni typo
- Chiarimenti
- Nuovi esempi
- Traduzioni

## 🛠️ Setup Ambiente di Sviluppo

### Prerequisiti

```bash
- Android Studio Hedgehog o successivo
- JDK 17 o successivo
- Android SDK 34
- Git
```

### Setup Iniziale

1. **Fork del repository**
   ```bash
   # Vai su GitHub e fai fork del repo
   ```

2. **Clone del fork**
   ```bash
   git clone https://github.com/your-username/Realtime_Chat.git
   cd Realtime_Chat
   ```

3. **Aggiungi remote upstream**
   ```bash
   git remote add upstream https://github.com/original-owner/Realtime_Chat.git
   ```

4. **Crea un branch**
   ```bash
   git checkout -b feature/amazing-feature
   ```

5. **Apri in Android Studio**
   - File > Open > Seleziona la cartella del progetto

### Mantieni il Fork Aggiornato

```bash
# Fetch da upstream
git fetch upstream

# Merge in main locale
git checkout main
git merge upstream/main

# Push al tuo fork
git push origin main
```

## 📝 Standard di Codice

### Kotlin Style Guide

Seguiamo le [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).

**Highlights:**

```kotlin
// ✅ GOOD
class MyViewModel(
    private val repository: SettingsRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(MyState())
    val uiState: StateFlow<MyState> = _uiState.asStateFlow()
    
    fun doSomething() {
        viewModelScope.launch {
            // Implementation
        }
    }
}

// ❌ BAD
class myViewModel(repository: SettingsRepository): ViewModel(){
    var uiState = MutableStateFlow(MyState())
    
    fun doSomething(){
        viewModelScope.launch{
            //Implementation
        }
    }
}
```

### Naming Conventions

- **Classes**: PascalCase (`MainActivity`, `RealtimeClient`)
- **Functions**: camelCase (`startRecording`, `sendImage`)
- **Constants**: UPPER_SNAKE_CASE (`MAX_RETRIES`, `DEFAULT_TIMEOUT`)
- **Private vars**: _camelCase con underscore (`_uiState`)
- **Resources**: snake_case (`main_screen`, `button_submit`)

### Composables

```kotlin
// ✅ GOOD - PascalCase per composables
@Composable
fun MainScreen(
    onNavigateToAdmin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel()
) {
    // Implementation
}

// Parameters order:
// 1. Required params
// 2. Optional params (con default)
// 3. Modifier (con default = Modifier)
// 4. ViewModel/Dependencies
```

### Comments & Documentation

```kotlin
/**
 * Gestisce la connessione alle Realtime API di OpenAI.
 *
 * @param apiKey La chiave API di OpenAI
 * @param model Il modello da utilizzare
 */
class RealtimeClient(
    private val apiKey: String,
    private val model: String = "gpt-4o-realtime-preview-2024-12-17"
) {
    /**
     * Invia un chunk audio all'API.
     *
     * @param audioBase64 Audio codificato in Base64 (PCM16)
     */
    fun sendAudio(audioBase64: String) {
        // Implementation
    }
}
```

### Error Handling

```kotlin
// ✅ GOOD
try {
    val result = performOperation()
    updateState(result)
} catch (e: NetworkException) {
    Log.e(TAG, "Network error", e)
    updateState(error = "Errore di rete: ${e.message}")
} catch (e: Exception) {
    Log.e(TAG, "Unexpected error", e)
    updateState(error = "Errore inaspettato")
}

// ❌ BAD - Cattura generica senza logging
try {
    performOperation()
} catch (e: Exception) {
    // Silent fail
}
```

## 🔄 Processo di Pull Request

### Checklist Prima della PR

- [ ] Codice segue gli standard del progetto
- [ ] Test aggiunti/aggiornati
- [ ] Test passano (`./gradlew test`)
- [ ] Build completa senza errori (`./gradlew build`)
- [ ] Documentazione aggiornata
- [ ] Commit messages sono descrittivi
- [ ] Branch è aggiornato con main

### Commit Messages

Usa [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

**Types:**
- `feat`: Nuova funzionalità
- `fix`: Bug fix
- `docs`: Solo documentazione
- `style`: Formattazione (non cambia logica)
- `refactor`: Refactoring (non aggiunge feature né fixa bug)
- `test`: Aggiunge o modifica test
- `chore`: Manutenzione (build, dependencies, etc.)

**Esempi:**

```bash
feat(audio): add noise cancellation
fix(camera): resolve crash on Galaxy S21
docs(readme): update setup instructions
test(realtime): add WebSocket connection tests
```

### Aprire una Pull Request

1. **Push del branch**
   ```bash
   git push origin feature/amazing-feature
   ```

2. **Apri PR su GitHub**
   - Vai al tuo fork su GitHub
   - Clicca "Compare & pull request"

3. **Compila il template della PR**
   ```markdown
   ## Descrizione
   Breve descrizione delle modifiche.
   
   ## Tipo di Cambiamento
   - [ ] Bug fix
   - [ ] Nuova feature
   - [ ] Breaking change
   - [ ] Documentazione
   
   ## Testing
   Come sono state testate le modifiche?
   
   ## Checklist
   - [ ] Il mio codice segue gli standard
   - [ ] Ho aggiornato la documentazione
   - [ ] Ho aggiunto test
   - [ ] Tutti i test passano
   ```

4. **Rispondi ai commenti**
   - Reviewers potrebbero richiedere modifiche
   - Sii aperto al feedback

5. **Merge**
   - Una volta approvata, la PR verrà merged

## 🧪 Testing

### Unit Tests

Posiziona i test in `app/src/test/`:

```kotlin
class MyFeatureTest {
    
    @Before
    fun setup() {
        // Setup test
    }
    
    @Test
    fun `test feature works correctly`() {
        // Given
        val input = "test"
        
        // When
        val result = processInput(input)
        
        // Then
        assertEquals("expected", result)
    }
}
```

### Eseguire i Test

```bash
# Tutti i test
./gradlew test

# Test specifici
./gradlew test --tests MyFeatureTest

# Con coverage
./gradlew test --coverage
```

### Coverage Minima

- Nuove feature: almeno 70% coverage
- Bug fix: aggiungere test che riproducono il bug

## 📚 Documentazione

### README

- Mantieni README.md aggiornato
- Aggiungi esempi per nuove feature
- Aggiorna screenshot se necessario

### Code Comments

```kotlin
// Commenti per logica complessa
// Spiega il "perché", non il "cosa"

// ✅ GOOD
// Usiamo PCM16 perché richiesto dalle Realtime API
val audioFormat = AudioFormat.ENCODING_PCM_16BIT

// ❌ BAD
// Setta il formato audio a PCM16
val audioFormat = AudioFormat.ENCODING_PCM_16BIT
```

### API Documentation

Usa KDoc per API pubbliche:

```kotlin
/**
 * Sends an image to the Realtime API.
 *
 * The image is automatically converted to Base64 and sent
 * with MIME type image/jpeg.
 *
 * @param imageBase64 Base64 encoded JPEG image
 * @throws IllegalStateException if not connected
 */
fun sendImage(imageBase64: String)
```

## 🎯 Priorità dei Contributi

### Alta Priorità
- Bug critici
- Problemi di sicurezza
- Performance degradation

### Media Priorità
- Nuove feature richieste
- Miglioramenti UX
- Test coverage

### Bassa Priorità
- Refactoring
- Documentazione
- Esempi aggiuntivi

## 🙏 Riconoscimenti

Tutti i contributori saranno aggiunti alla lista dei ringraziamenti nel README.

## 📧 Domande?

- Apri un [Discussion](https://github.com/yourusername/Realtime_Chat/discussions)
- Contatta i maintainer
- Unisciti alla community

---

**Grazie per aver contribuito a Realtime Chat! 🚀**

Ogni contributo, grande o piccolo, è apprezzato e aiuta a migliorare il progetto per tutti.
