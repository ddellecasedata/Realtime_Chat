package com.things5.realtimechat

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Test
import org.junit.Assert.*

/**
 * Test per verificare che i parametri dei tool MCP mantengano i tipi corretti
 */
class McpParameterTypeTest {
    
    private val gson = Gson()
    
    @Test
    fun `test boolean parameter is preserved`() {
        // Simula i parametri ricevuti dal RealtimeClient
        val parameters = mapOf<String, Any>(
            "is_connected" to true,
            "name" to "test_server"
        )
        
        // Converti come fa McpBridge
        val argumentsJson = JsonObject()
        parameters.forEach { (key, value) ->
            when (value) {
                is Boolean -> argumentsJson.addProperty(key, value)
                is Number -> argumentsJson.addProperty(key, value)
                is String -> argumentsJson.addProperty(key, value)
                else -> {
                    try {
                        val jsonElement = gson.toJsonTree(value)
                        argumentsJson.add(key, jsonElement)
                    } catch (e: Exception) {
                        argumentsJson.addProperty(key, value.toString())
                    }
                }
            }
        }
        
        val jsonRpcRequest = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("id", 1)
            addProperty("method", "tools/call")
            add("params", JsonObject().apply {
                addProperty("name", "update_server_status")
                add("arguments", argumentsJson)
            })
        }
        
        val json = gson.toJson(jsonRpcRequest)
        
        // Verifica che il JSON sia corretto
        println("Generated JSON: $json")
        assertTrue("JSON should contain 'is_connected':true", json.contains("\"is_connected\":true"))
        assertFalse("JSON should not contain 'is_connected':\"true\"", json.contains("\"is_connected\":\"true\""))
        
        // Parse back per verificare i tipi
        val parsed = gson.fromJson(json, JsonObject::class.java)
        val params = parsed.getAsJsonObject("params")
        val args = params.getAsJsonObject("arguments")
        
        assertTrue("is_connected should be boolean", args.get("is_connected").isJsonPrimitive)
        assertTrue("is_connected should be boolean primitive", args.get("is_connected").asJsonPrimitive.isBoolean)
        assertEquals(true, args.get("is_connected").asBoolean)
        
        assertTrue("name should be string", args.get("name").isJsonPrimitive)
        assertTrue("name should be string primitive", args.get("name").asJsonPrimitive.isString)
        assertEquals("test_server", args.get("name").asString)
    }
    
    @Test
    fun `test number parameters are preserved`() {
        val parameters = mapOf<String, Any>(
            "count" to 42,
            "ratio" to 3.14,
            "enabled" to false
        )
        
        val argumentsJson = JsonObject()
        parameters.forEach { (key, value) ->
            when (value) {
                is Boolean -> argumentsJson.addProperty(key, value)
                is Number -> argumentsJson.addProperty(key, value)
                is String -> argumentsJson.addProperty(key, value)
                else -> {
                    try {
                        val jsonElement = gson.toJsonTree(value)
                        argumentsJson.add(key, jsonElement)
                    } catch (e: Exception) {
                        argumentsJson.addProperty(key, value.toString())
                    }
                }
            }
        }
        
        val json = gson.toJson(argumentsJson)
        println("Generated JSON: $json")
        
        // Parse back
        val parsed = gson.fromJson(json, JsonObject::class.java)
        
        assertEquals(42, parsed.get("count").asInt)
        assertEquals(3.14, parsed.get("ratio").asDouble, 0.001)
        assertEquals(false, parsed.get("enabled").asBoolean)
    }
    
    @Test
    fun `test mixed parameters are preserved correctly`() {
        val parameters = mapOf<String, Any>(
            "server_id" to "srv_123",
            "port" to 8080,
            "ssl_enabled" to true,
            "timeout" to 30.5
        )
        
        val argumentsJson = JsonObject()
        parameters.forEach { (key, value) ->
            when (value) {
                is Boolean -> argumentsJson.addProperty(key, value)
                is Number -> argumentsJson.addProperty(key, value)
                is String -> argumentsJson.addProperty(key, value)
                else -> {
                    try {
                        val jsonElement = gson.toJsonTree(value)
                        argumentsJson.add(key, jsonElement)
                    } catch (e: Exception) {
                        argumentsJson.addProperty(key, value.toString())
                    }
                }
            }
        }
        
        val json = gson.toJson(argumentsJson)
        println("Generated JSON: $json")
        
        // Parse back and verify all types
        val parsed = gson.fromJson(json, JsonObject::class.java)
        
        assertTrue("server_id should be string", parsed.get("server_id").asJsonPrimitive.isString)
        assertEquals("srv_123", parsed.get("server_id").asString)
        
        assertTrue("port should be number", parsed.get("port").asJsonPrimitive.isNumber)
        assertEquals(8080, parsed.get("port").asInt)
        
        assertTrue("ssl_enabled should be boolean", parsed.get("ssl_enabled").asJsonPrimitive.isBoolean)
        assertEquals(true, parsed.get("ssl_enabled").asBoolean)
        
        assertTrue("timeout should be number", parsed.get("timeout").asJsonPrimitive.isNumber)
        assertEquals(30.5, parsed.get("timeout").asDouble, 0.001)
    }
    
    @Test
    fun `test parameter types from OpenAI are correctly handled`() {
        // Simula esattamente cosa arriva da RealtimeClient dopo il parsing
        // di response.function_call_arguments.done
        val argumentsStr = """{"is_connected": true, "server_name": "test"}"""
        val argsJson = gson.fromJson(argumentsStr, JsonObject::class.java)
        
        val params = argsJson.entrySet().associate { entry ->
            val value: Any = when {
                entry.value.isJsonPrimitive -> {
                    val primitive = entry.value.asJsonPrimitive
                    when {
                        primitive.isBoolean -> primitive.asBoolean
                        primitive.isNumber -> {
                            val num = primitive.asNumber
                            if (num.toDouble() == num.toInt().toDouble()) {
                                num.toInt()
                            } else {
                                num.toDouble()
                            }
                        }
                        else -> primitive.asString
                    }
                }
                entry.value.isJsonArray -> entry.value.asJsonArray.toString()
                entry.value.isJsonObject -> entry.value.asJsonObject.toString()
                else -> entry.value.toString()
            }
            entry.key to value
        }
        
        // Verifica che i tipi siano corretti
        assertTrue("is_connected should be Boolean", params["is_connected"] is Boolean)
        assertEquals(true, params["is_connected"])
        
        assertTrue("server_name should be String", params["server_name"] is String)
        assertEquals("test", params["server_name"])
        
        // Ora converti come fa McpBridge
        val argumentsJson = JsonObject()
        params.forEach { (key, value) ->
            when (value) {
                is Boolean -> argumentsJson.addProperty(key, value)
                is Number -> argumentsJson.addProperty(key, value)
                is String -> argumentsJson.addProperty(key, value)
                else -> {
                    try {
                        val jsonElement = gson.toJsonTree(value)
                        argumentsJson.add(key, jsonElement)
                    } catch (e: Exception) {
                        argumentsJson.addProperty(key, value.toString())
                    }
                }
            }
        }
        
        val json = gson.toJson(argumentsJson)
        println("Final JSON sent to MCP: $json")
        
        // Verifica il JSON finale
        assertTrue("JSON should contain 'is_connected':true", json.contains("\"is_connected\":true"))
        assertFalse("JSON should NOT contain 'is_connected':\"true\"", json.contains("\"is_connected\":\"true\""))
    }
}
