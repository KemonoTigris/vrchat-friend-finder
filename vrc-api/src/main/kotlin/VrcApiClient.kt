package com.kemonotigris

import io.ktor.client.HttpClient
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
import java.io.File
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
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
    val statusDescription: String? = null,
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

// Existing data classes (unchanged)

@Serializable
data class AuthVerificationResponse(
    val ok: Boolean,
    val token: String? = null
)

class VrcApiClient(
    private val username: String,
    private val password: String,
    private val cookieStoragePath: String = "vrc_auth_cookie.txt" // File to store the cookie
) {
    private val mutex = Mutex()
    private var lastRequestTime = 0L
    private var authCookie: String? = null
    private var cookieExpiresAt: Long? = null // Store expiration timestamp
    private val baseUrl = "https://api.vrchat.cloud/api/1"
    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        prettyPrint = true
        isLenient = true
    }

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
            level = io.ktor.client.plugins.logging.LogLevel.ALL
        }
        defaultRequest {
            url(baseUrl)
            contentType(ContentType.Application.Json)
        }
    }

    init {
        // Load saved cookie on initialization
        loadSavedCookie()
    }

    private fun loadSavedCookie() {
        try {
            val file = File(cookieStoragePath)
            if (file.exists()) {
                val lines = file.readLines()
                if (lines.size >= 2) {
                    authCookie = lines[0]
                    cookieExpiresAt = lines[1].toLongOrNull()
                    println("Loaded saved auth cookie, expires: ${cookieExpiresAt?.let { Instant.ofEpochMilli(it) }}")
                }
            }
        } catch (e: Exception) {
            println("Failed to load saved cookie: ${e.message}")
            authCookie = null
            cookieExpiresAt = null
        }
    }

    private fun saveCookie(cookie: String, expiryTimestamp: Long) {
        try {
            val file = File(cookieStoragePath)
            file.writeText("$cookie\n$expiryTimestamp")
            println("Auth cookie saved successfully")
        } catch (e: Exception) {
            println("Failed to save auth cookie: ${e.message}")
        }
    }

    /**
     * Checks if the current auth token is valid by calling the /auth endpoint.
     */
    suspend fun isAuthTokenValid(): Boolean {
        return verifyAuthCookie()
    }

    private suspend fun verifyAuthCookie(): Boolean {
        try {
            if (authCookie == null) return false

            val response = client.get("$baseUrl/auth") {
                header("Cookie", "auth=$authCookie")
            }

            if (response.status.value in 200..299) {
                val responseText = response.bodyAsText()
                val authResponse = jsonConfig.decodeFromString<AuthVerificationResponse>(responseText)
                return authResponse.ok
            }
        } catch (e: Exception) {
            println("Failed to verify auth cookie: ${e.message}")
        }
        return false
    }

    private suspend fun ensureAuthenticated() {
        // First check if we have a non-expired cookie
        if (authCookie != null && cookieExpiresAt != null) {
            val now = Instant.now().toEpochMilli()

            // Only verify if the cookie hasn't expired based on our stored expiry
            if (cookieExpiresAt!! > now && verifyAuthCookie()) {
                println("Using saved auth cookie")
                return
            }

            println("Saved auth cookie is expired or invalid, re-authenticating...")
        }

        println("Attempting authentication...")
        val response = client.get("$baseUrl/auth/user") {
            basicAuth(username, password)
        }

        if (response.status.value in 200..299) {
            val responseText = response.bodyAsText()

            // Extract cookie from response headers
            val setCookieHeader = response.headers["Set-Cookie"]
            println("Set-Cookie header: $setCookieHeader")

            val extractedCookie = setCookieHeader?.split(";")?.firstOrNull {
                it.trim().startsWith("auth=")
            }?.trim()?.removePrefix("auth=")

            // Extract expiry date from Set-Cookie header
            val expiryStr = setCookieHeader?.split(";")?.firstOrNull {
                it.trim().lowercase().startsWith("expires=")
            }?.trim()?.removePrefix("Expires=")

            // Parse expiry date or use a default (one year)
            val expiry = if (expiryStr != null) {
                try {
                    val formatter = DateTimeFormatter.RFC_1123_DATE_TIME
                    val dateTime = ZonedDateTime.parse(expiryStr, formatter)
                    dateTime.toInstant().toEpochMilli()
                } catch (e: Exception) {
                    println("Failed to parse cookie expiry date: $expiryStr, error: ${e.message}")
                    Instant.now().plus(365, ChronoUnit.DAYS).toEpochMilli()
                }
            } else {
                Instant.now().plus(365, ChronoUnit.DAYS).toEpochMilli()
            }

            // Store the cookie temporarily
            authCookie = extractedCookie
            cookieExpiresAt = expiry

            if (responseText.contains("requiresTwoFactorAuth")) {
                println("2FA is required for this account.")
                completeEmailOtpVerification()
                return
            }

            if (extractedCookie != null) {
                println("Authentication successful. Auth cookie expires: ${Instant.ofEpochMilli(expiry)}")
                saveCookie(extractedCookie, expiry)
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
        val initialAuthCookie = authCookie

        if (initialAuthCookie == null) {
            println("Failed to extract initial auth cookie for 2FA verification")
            throw Exception("Failed to extract initial auth cookie")
        }

        println("Email OTP verification required.")
        println("Please check your email and enter the verification code:")
        val otpCode = readlnOrNull() ?: ""

        val jsonBody = """{"code":"$otpCode"}"""

        val response = client.post("$baseUrl/auth/twofactorauth/emailotp/verify") {
            contentType(ContentType.Application.Json)
            header("Cookie", "auth=$initialAuthCookie")
            setBody(jsonBody)
        }

        println("2FA verification response status: ${response.status}")

        if (response.status.value in 200..299) {
            val newSetCookieHeader = response.headers["Set-Cookie"]
            println("2FA verification Set-Cookie header: $newSetCookieHeader")

            // Extract new cookie and expiry
            val newAuthCookie = newSetCookieHeader?.split(";")?.firstOrNull {
                it.trim().startsWith("auth=")
            }?.trim()?.removePrefix("auth=")

            // Extract expiry date from Set-Cookie header
            val expiryStr = newSetCookieHeader?.split(";")?.firstOrNull {
                it.trim().lowercase().startsWith("expires=")
            }?.trim()?.removePrefix("Expires=")

            val expiry = if (expiryStr != null) {
                try {
                    val formatter = DateTimeFormatter.RFC_1123_DATE_TIME
                    val dateTime = ZonedDateTime.parse(expiryStr, formatter)
                    dateTime.toInstant().toEpochMilli()
                } catch (e: Exception) {
                    println("Failed to parse cookie expiry date: $expiryStr")
                    Instant.now().plus(365, ChronoUnit.DAYS).toEpochMilli()
                }
            } else {
                Instant.now().plus(365, ChronoUnit.DAYS).toEpochMilli()
            }

            if (newAuthCookie != null) {
                authCookie = newAuthCookie
                cookieExpiresAt = expiry
                println("2FA verification successful. New auth cookie expires: ${Instant.ofEpochMilli(expiry)}")
                saveCookie(newAuthCookie, expiry)
            } else {
                println("No new cookie provided after 2FA verification. Keeping initial cookie.")
                saveCookie(initialAuthCookie, expiry)
            }
        } else {
            println("2FA verification failed with status: ${response.status}")
            val errorBody = response.bodyAsText()
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

        // Ensure we have a valid auth cookie before making the request
        if (authCookie == null || !verifyAuthCookie()) {
            ensureAuthenticated()
        }

        println("Requesting user info for $userId")
        try {
            println("Using cookie: $authCookie")

            val response = client.get("$baseUrl/users/$userId") {
                contentType(ContentType.Application.Json)
                header("Cookie", "auth=$authCookie")
                accept(ContentType.Application.Json)
            }

            println("User info response status: ${response.status}")
            if (response.status.value in 200..299) {
                val responseText = response.bodyAsText()
                return jsonConfig.decodeFromString(responseText)
            } else {
                try {
                    val errorResponse = jsonConfig.decodeFromString<ErrorResponse>(response.bodyAsText())
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
