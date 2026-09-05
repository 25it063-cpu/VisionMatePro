package com.visionmate.pro.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.visionmate.pro.BuildConfig
import com.visionmate.pro.model.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiVisionAnalyzer {

    // Switched to "gemini-1.5-flash-latest" which is more reliable across different API versions
    private val model = GenerativeModel(
        modelName = "gemini-1.5-flash-latest",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    suspend fun analyzeFrame(bitmap: Bitmap, language: AppLanguage): String? =
        withContext(Dispatchers.IO) {
            if (BuildConfig.GEMINI_API_KEY.isEmpty() || BuildConfig.GEMINI_API_KEY == "YOUR_GEMINI_API_KEY") {
                Log.w("GeminiVision", "No valid API key found")
                return@withContext null
            }

            try {
                val langInstruction = when (language) {
                    AppLanguage.TAMIL -> "Respond in Tamil only."
                    AppLanguage.HINDI -> "Respond in Hindi only."
                    AppLanguage.TELUGU -> "Respond in Telugu only."
                    AppLanguage.MALAYALAM -> "Respond in Malayalam only."
                    else -> "Respond in English."
                }

                val prompt = """
                    Identify immediate obstacles or hazards in this frame for a visually impaired person.
                    Provide a very short alert (max 5 words).
                    If clear, respond with: CLEAR
                    $langInstruction
                """.trimIndent()

                val response = model.generateContent(
                    content {
                        image(bitmap)
                        text(prompt)
                    }
                )

                val result = response.text?.trim() ?: return@withContext null
                
                if (result.contains("CLEAR", ignoreCase = true)) return@withContext null

                return@withContext result

            } catch (e: Exception) {
                Log.e("GeminiVision", "AI Analysis failed: ${e.message}")
                // Throw so ViewModel can track that AI/Internet is currently down
                throw e
            }
        }
}
