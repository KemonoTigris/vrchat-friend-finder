package com.kemonotigris

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.accept
import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class VrcUserInfo(
    val id: String,
    val displayName: String? = null,
    val bio: String? = null,
    val bioLinks: List<String>? = null,
    val currentAvatarImageUrl: String? = null,
    val currentAvatarThumbnailImageUrl: String? = null,
    val status: String? = null,
    val isFriend: Boolean = false,
    val lastLogin: String? = null
)

@Serializable
data class ErrorResponse(
    val error: ErrorDetail
)

@Serializable
data class ErrorDetail(
    val message: String,
    val status_code: Int
)

class VrcApiClient(
    private val username: String,
    private val password: String
) {
    private val mutex = Mutex()
    private var lastRequestTime = 0L
    private var authCookie: String? = ""
    private val baseUrl = "https://api.vrchat.cloud/api/1"

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
                prettyPrint = true
                isLenient = true
            }
        }
        install(Logging) {
            level = io.ktor.client.plugins.logging.LogLevel.ALL  // Log everything: headers, body, etc.
        }
        defaultRequest {
            url(baseUrl)
            contentType(ContentType.Application.Json)
        }
    }


    private suspend fun ensureAuthenticated() {
        println("Attempting authentication...")
        val response = client.get("$baseUrl/auth/user") {
            basicAuth(username, password)
        }

        if (response.status.value in 200..299) {
            // Parse the response to check for 2FA requirement
            val responseText = response.body<String>()
            println("Auth response body: $responseText")

            // Temporarily store the response for use in completeEmailOtpVerification
            val setCookieHeader = response.headers["Set-Cookie"]
            val initialAuthCookie = setCookieHeader?.split(";")?.firstOrNull {
                it.trim().startsWith("auth=")
            }?.trim()?.removePrefix("auth=")

            // Store this temporarily
            authCookie = initialAuthCookie

            if (responseText.contains("requiresTwoFactorAuth")) {
                // Need to do 2FA verification
                println("2FA is required for this account.")
                completeEmailOtpVerification()
                return
            }

            // Normal authentication, extract cookie
            println("Auth response status: ${response.status}")
            println("Set-Cookie header: $setCookieHeader")

            if (initialAuthCookie != null) {
                // We've already stored the cookie above
                println("Authentication successful. Auth cookie: $authCookie")
            } else {
                println("Authentication response didn't contain auth cookie")
                throw Exception("Failed to extract auth cookie from response")
            }
        } else {
            println("Authentication failed with status: ${response.status}")
            throw Exception("Authentication failed: ${response.status}")
        }
    }

    private suspend fun completeEmailOtpVerification() {
        // We need to reference the authCookie that was stored in ensureAuthenticated
        val initialAuthCookie = authCookie

        if (initialAuthCookie == null) {
            println("Failed to extract initial auth cookie for 2FA verification")
            throw Exception("Failed to extract initial auth cookie")
        }

        println("Email OTP verification required.")
        println("Please check your email and enter the verification code:")
        val otpCode = readlnOrNull() ?: ""

        // Use a raw JSON string
        val jsonBody = """{"code":"$otpCode"}"""

        // According to the docs, we need to use the cookie from the initial response
        val response = client.post("$baseUrl/auth/twofactorauth/emailotp/verify") {
            contentType(ContentType.Application.Json)

            // Use cookie auth instead of basic auth
            header("Cookie", "auth=$initialAuthCookie")

            // Use a raw JSON string for the body
            setBody(jsonBody)
        }

        println("2FA verification response status: ${response.status}")

        if (response.status.value in 200..299) {
            val newSetCookieHeader = response.headers["Set-Cookie"]
            println("2FA verification Set-Cookie header: $newSetCookieHeader")

            // Extract the auth cookie from the 2FA response
            val authCookieValue = newSetCookieHeader?.split(";")?.firstOrNull {
                it.trim().startsWith("auth=")
            }?.trim()?.removePrefix("auth=")

            if (authCookieValue != null) {
                authCookie = authCookieValue
                println("2FA verification successful. Auth cookie: $authCookie")
            } else {
                // If no new cookie is provided, we keep using the initial one
                println("No new cookie provided after 2FA verification. Keeping initial cookie: $initialAuthCookie")
            }
        } else {
            println("2FA verification failed with status: ${response.status}")
            val errorBody = response.body<String>()
            println("Error response: $errorBody")
            throw Exception("2FA verification failed: ${response.status}")
        }
    }

    private suspend fun rateLimit() {
        mutex.withLock {
            val now = Instant.now().toEpochMilli()
            val timeSinceLastRequest = now - lastRequestTime
            val minInterval = 1000L // 1 second between requests to avoid rate limiting

            if (timeSinceLastRequest < minInterval) {
                delay(minInterval - timeSinceLastRequest)
            }
            lastRequestTime = Instant.now().toEpochMilli()
        }
    }

    suspend fun getUserInfo(userId: String): VrcUserInfo {
        rateLimit()

        if (authCookie == null) {
            ensureAuthenticated()
        }

        println("Requesting user info for $userId")
        try {
            println("Using cookie: $authCookie")

            val response = client.get("$baseUrl/users/$userId") {
                contentType(ContentType.Application.Json)
                header("Cookie", "auth=$authCookie")
                accept(ContentType.Application.Json)  // Add this to ensure correct Accept header
            }

            println("User info response status: ${response.status}")
            if (response.status.value in 200..299) {
                // Parse the response as String first to debug
                val responseText = response.bodyAsText()
                println("User info response body: ${responseText.take(100)}...")  // Print first 100 chars

                // Try manually deserializing with kotlinx.serialization
                return Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    coerceInputValues = true
                }.decodeFromString(responseText)
            } else {
                try {
                    val errorResponse = response.body<ErrorResponse>()
                    throw Exception("API error: ${errorResponse.error.message} (${errorResponse.error.status_code})")
                } catch (e: Exception) {
                    throw Exception("Failed to get user info: ${response.status}")
                }
            }
        } catch (e: Exception) {
            println("Error getting user info: ${e.message}")
            throw e
        }
    }

    fun close() {
        client.close()
    }
}