package com.kemonotigris

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
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

class VrcApiClient(
    private val username: String,
    private val password: String
) {
    private val mutex = Mutex()
    private var lastRequestTime = 0L
    private var authCookie: String? = null

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
                prettyPrint = true
                isLenient = true
            }
        }
        defaultRequest {
            contentType(ContentType.Application.Json)
            // VRChat API requires this API key for all requests (it's publicly known)
//            parameter("apiKey", "")
        }
    }

    private suspend fun ensureAuthenticated() {
        if (authCookie == null) {
            val response = client.post("https://api.vrchat.cloud/api/1/auth/user") {
                basicAuth(username, password)
            }

            if (response.status.isSuccess()) {
                val cookies = response.headers["Set-Cookie"]
                authCookie = cookies?.split(";")?.firstOrNull { it.startsWith("auth=") }
            } else {
                throw Exception("Authentication failed: ${response.status}")
            }
        }
    }

    private suspend fun rateLimit() {
        mutex.withLock {
            val now = Instant.now().toEpochMilli()
            val timeSinceLastRequest = now - lastRequestTime
            val minInterval = 10_000L // 10 seconds

            if (timeSinceLastRequest < minInterval) {
                delay(minInterval - timeSinceLastRequest)
            }
            lastRequestTime = Instant.now().toEpochMilli()
        }
    }

    suspend fun getUserInfo(userId: String): VrcUserInfo {
        rateLimit()
        ensureAuthenticated()

        return client.get("https://api.vrchat.cloud/api/1/users/$userId") {
            headers {
                append(HttpHeaders.Cookie, authCookie ?: "")
            }
        }.body()
    }

    fun close() {
        client.close()
    }
}