package com.example.data.api

import com.example.BuildConfig
import com.example.data.model.TaskEntity
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class TaskSuggestion(
    val title: String,
    val description: String,
    val priority: String, // "High", "Medium", "Low"
    val category: String  // "Work", "Personal", "Health", "Shopping", "Other"
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    val responseMimeType: String? = null,
    val responseSchema: GeminiSchema? = null,
    val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class GeminiSchema(
    val type: String,
    val items: GeminiSchema? = null,
    val properties: Map<String, GeminiSchema>? = null,
    val required: List<String>? = null,
    val enum: List<String>? = null,
    val description: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    val content: GeminiContent?
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val apiService: GeminiApiService = retrofit.create(GeminiApiService::class.java)

    /**
     * Checks if the Gemini API Key is configured in secrets or .env
     */
    fun isApiKeyAvailable(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotEmpty() && key != "MY_GEMINI_API_KEY"
    }

    /**
     * Fetches smart task suggestions from Gemini based on existing tasks and desired category.
     */
    suspend fun getTaskSuggestions(
        existingTasks: List<TaskEntity>,
        categoryFilter: String
    ): List<TaskSuggestion> {
        if (!isApiKeyAvailable()) {
            throw IllegalStateException("Gemini API Key is not configured. Please enter your API Key in the Secrets panel in Google AI Studio.")
        }

        val prompt = buildPrompt(existingTasks, categoryFilter)

        val schema = GeminiSchema(
            type = "ARRAY",
            items = GeminiSchema(
                type = "OBJECT",
                properties = mapOf(
                    "title" to GeminiSchema(type = "STRING", description = "Short, crisp, actionable title of the suggested task"),
                    "description" to GeminiSchema(type = "STRING", description = "Brief, clear description of what needs to be done"),
                    "priority" to GeminiSchema(type = "STRING", enum = listOf("High", "Medium", "Low"), description = "The urgency of this task"),
                    "category" to GeminiSchema(type = "STRING", enum = listOf("Work", "Personal", "Health", "Shopping", "Other"), description = "Matching category")
                ),
                required = listOf("title", "description", "priority", "category")
            )
        )

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(parts = listOf(GeminiPart(text = prompt)))
            ),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = "application/json",
                responseSchema = schema,
                temperature = 0.7f
            )
        )

        val response = apiService.generateContent(BuildConfig.GEMINI_API_KEY, request)
        val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw IllegalStateException("Received an empty response from Gemini.")

        return parseSuggestionsList(responseText)
    }

    private fun parseSuggestionsList(jsonText: String): List<TaskSuggestion> {
        val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, TaskSuggestion::class.java)
        val adapter = moshi.adapter<List<TaskSuggestion>>(listType)
        return adapter.fromJson(jsonText) ?: emptyList()
    }

    private fun buildPrompt(existingTasks: List<TaskEntity>, categoryFilter: String): String {
        val tasksText = if (existingTasks.isEmpty()) {
            "No current tasks."
        } else {
            existingTasks.take(15).joinToString("\n") { task ->
                "- [${task.category}] ${task.title}: ${task.description} (Priority: ${task.priority}, Completed: ${task.isCompleted})"
            }
        }

        val focusText = if (categoryFilter != "All") {
            "Focus primarily on generating tasks within the category '$categoryFilter'."
        } else {
            "Provide a balanced mix of tasks across categories like Work, Personal, Health, Shopping, and Other."
        }

        return """
            You are an advanced Productivity Coach and AI task manager.
            Based on the user's current tasks list, suggest exactly 4 new context-aware, creative, and highly actionable tasks to help them progress.
            
            Current user tasks:
            $tasksText
            
            Instructions:
            1. $focusText
            2. Do NOT suggest tasks that are identical or highly similar to existing ones.
            3. Each suggestion must have an actionable title and clear, brief description.
            4. Assign an appropriate priority ('High', 'Medium', or 'Low') and a valid category ('Work', 'Personal', 'Health', 'Shopping', or 'Other').
            
            Output strictly valid JSON complying with the requested schema. Do not include markdown formatting.
        """.trimIndent()
    }
}
