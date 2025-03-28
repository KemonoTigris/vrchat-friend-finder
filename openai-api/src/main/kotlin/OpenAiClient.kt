package com.kemonotigris

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Client for interacting with OpenAI's Responses API to analyze compatibility
 * with VRChat users and generate conversation starters
 */
class OpenAiClient(private val apiKey: String) {

    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30000  // 30 seconds timeout
            connectTimeoutMillis = 30000
            socketTimeoutMillis = 30000
        }
    }

    private val jsonFormat = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Result of compatibility analysis
     */
    @Serializable
    data class CompatibilityResult(
        val compatibilityScore: Int,
        val compatibilityReason: String,
        val suggestedQuestions: List<String>
    )

    /**
     * OpenAI API response structure
     */
    @Serializable
    data class OpenAiResponse(
        val id: String,
        val output: List<OutputItem>,
        val error: ApiError? = null
    )

    @Serializable
    data class ApiError(
        val type: String,
        val message: String
    )

    @Serializable
    data class OutputItem(
        val type: String,
        val content: List<ContentItem>? = null,
        val role: String? = null,
        val status: String? = null,
        val id: String? = null
    )

    @Serializable
    data class ContentItem(
        val type: String,
        val text: String
    )

    /**
     * Analyzes compatibility between the user and a VRChat user
     *
     * @param myInfo Information about the user running the app
     * @param vrchatUserInfo Information about the VRChat user
     * @return A CompatibilityResult containing score, reason, and suggested questions
     */
    suspend fun analyzeCompatibility(
        myInfo: String,
        vrchatUserInfo: VrcUserInfo
    ): CompatibilityResult = withContext(Dispatchers.IO) {
        val prompt = createPrompt(myInfo, vrchatUserInfo)
        val requestBody = createRequestBody(prompt)

        try {
            val response = client.post("https://api.openai.com/v1/responses") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(requestBody)
            }

            if (response.status != HttpStatusCode.OK) {
                val errorBody = response.bodyAsText()
                throw IOException("Unexpected response code: ${response.status}, message: $errorBody")
            }

            val responseBody = response.bodyAsText()
            val openAiResponse = jsonFormat.decodeFromString<OpenAiResponse>(responseBody)

            // Check if the API returned an error
            if (openAiResponse.error != null) {
                throw IOException("OpenAI API error: ${openAiResponse.error.message}")
            }

            // Extract the structured JSON output from the response
            val messageContent = openAiResponse.output
                .firstOrNull { it.type == "message" }
                ?.content
                ?.firstOrNull { it.type == "output_text" }
                ?.text
                ?: throw IOException("No valid output content found")

            // Parse the JSON output into our model
            jsonFormat.decodeFromString<CompatibilityResult>(messageContent)

        } catch (e: Exception) {
            throw IOException("Failed to get response from OpenAI API: ${e.message}", e)
        }
    }

    /**
     * Creates a prompt for the OpenAI API based on user information
     */
    private fun createPrompt(myInfo: String, userInfo: VrcUserInfo): String {
        return """
            Analyze the compatibility between me and another VRChat user.
            
            Information about me:
            $myInfo
            
            Information about the VRChat user:
            - Display Name: ${userInfo.displayName}
            - Bio: ${userInfo.bio}
            - Bio Links: ${userInfo.bioLinks?.joinToString(", ") ?: "None"}
            - Status Description: ${userInfo.statusDescription}
            
            Based on the provided information, determine our compatibility score, explain the reasoning, and suggest some questions I could ask when I first meet this person in VRChat to start a meaningful conversation.
            
            The response should be a JSON object with the following structure:
            {
              "compatibilityScore": (a number between 1 and 100),
              "compatibilityReason": "detailed explanation of the compatibility score",
              "suggestedQuestions": ["question 1", "question 2", "question 3", ...]
            }
        """.trimIndent()
    }

    /**
     * Creates the JSON request body for the OpenAI API call
     */
    private fun createRequestBody(prompt: String): String {
        val requestJsonObject = buildJsonObject {
            put("model", "gpt-4.5-preview-2025-02-27")
            put("input", prompt)

            putJsonObject("text") {
                putJsonObject("format") {
                    put("type", "json_object")
                }
            }
        }

        // Encode the JSON object to a string
        return jsonFormat.encodeToString(JsonObject.serializer(), requestJsonObject)
    }

    /**
     * Clean up resources when done
     */
    fun close() {
        client.close()
    }
}