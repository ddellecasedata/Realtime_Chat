# Security Improvement Plan

## Current Implementation (Insecure)
```
User enters API key → Stored in SharedPreferences → Used directly
```

**Risk**: API key exposed on device. Se il device è compromesso, l'attacker ha accesso completo.

## Recommended Implementation (Secure)

### Architecture
```
Android App → Your Backend → OpenAI API
```

### Backend Endpoint (Node.js/Python/Java)

**POST /api/realtime/token**
```javascript
// Example Node.js endpoint
app.post('/api/realtime/token', authenticateUser, async (req, res) => {
  const response = await fetch('https://api.openai.com/v1/realtime/client_secrets', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${process.env.OPENAI_API_KEY}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      expires_after: {
        anchor: 'created_at',
        seconds: 600  // 10 minutes
      },
      session: {
        type: 'realtime',
        model: 'gpt-realtime'
      }
    })
  });
  
  const data = await response.json();
  res.json({ ephemeral_key: data.value });
});
```

### Android Changes

**1. Add backend URL to Settings**
```kotlin
data class AppSettings(
    val backendUrl: String = "https://your-backend.com",
    val openaiApiKey: String = "",  // Keep for legacy/testing
    val mcpServers: List<McpServerConfig> = emptyList()
)
```

**2. Network service to fetch token**
```kotlin
// app/src/main/java/com/things5/realtimechat/network/TokenService.kt
class TokenService(private val backendUrl: String) {
    suspend fun getEphemeralToken(): String {
        val response = client.post("$backendUrl/api/realtime/token") {
            // Add authentication headers
        }
        return response.body<TokenResponse>().ephemeralKey
    }
}
```

**3. Update RealtimeClient initialization**
```kotlin
// In MainViewModel
val token = tokenService.getEphemeralToken()
realtimeClient = RealtimeClient(apiKey = token)
```

## Benefits

1. **Security**: API key never leaves your server
2. **Control**: Can revoke access server-side
3. **Monitoring**: Track usage per user
4. **Rate limiting**: Implement on backend
5. **Cost control**: Limit token lifetime

## Migration Path

### Phase 1 (Current - Testing)
- Keep direct API key for development
- Add warning in UI

### Phase 2 (Production)
- Deploy backend service
- Add backend URL to settings
- Implement token fetch
- Optional: Keep API key as fallback

### Phase 3 (Secure)
- Remove direct API key option
- Require backend authentication
- Implement user accounts
