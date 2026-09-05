package com.visionmate.pro.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.visionmate.pro.BuildConfig
import com.visionmate.pro.model.CommandIntent
import com.visionmate.pro.model.VoiceCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class GeminiManager {
    private val apiKey = BuildConfig.GEMINI_API_KEY 
    
    // Using "gemini-1.5-flash" which is the standard name.
    private val model = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = apiKey
    )

    suspend fun classifyIntent(candidates: List<String>): VoiceCommand = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty() || apiKey == "YOUR_GEMINI_API_KEY") {
            return@withContext com.visionmate.pro.intent.IntentClassifier.classify(candidates[0])
        }

        val combinedInput = candidates.take(3).joinToString(" | ")
        
        val prompt = """
            Determine the user intent for a visually impaired assistant.
            Intents: NAVIGATE (extract 'destination'), CHANGE_LANGUAGE (extract code: en, ta, hi, te, ml), EMERGENCY, GREETING, BATTERY_STATUS, FIND_CANE.
            User said: "$combinedInput"
            Return JSON: {"intent": "NAME", "params": {}}
        """.trimIndent()

        return@withContext try {
            val response = model.generateContent(prompt)
            val text = response.text ?: ""
            val jsonStr = text.substringAfter("{").substringBeforeLast("}") + "}"
            val json = JSONObject(jsonStr)
            
            val intentStr = json.getString("intent")
            val params = mutableMapOf<String, String>()
            if (json.has("params")) {
                val pObj = json.getJSONObject("params")
                pObj.keys().forEach { key -> params[key] = pObj.getString(key) }
            }

            VoiceCommand(candidates[0], CommandIntent.valueOf(intentStr), params)
        } catch (e: Exception) {
            com.visionmate.pro.intent.IntentClassifier.classify(candidates[0])
        }
    }

    suspend fun classifyIntentWithFunctions(candidates: List<String>): VoiceCommand = classifyIntent(candidates)
}
