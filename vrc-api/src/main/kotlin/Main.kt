package com.kemonotigris

import io.ktor.client.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/**
 * A placeholder VRChat API client that:
 *  - Rate-limits requests to 1 every 10 seconds
 *  - Returns dummy data
 */
class VrcApiClient {
    private val client = HttpClient()
    private val requestMutex = Mutex()
    private var lastRequestTime = 0L

    suspend fun getUserInfo(userId: String): VrcUserInfo {
        requestMutex.withLock {
            val now = Instant.now().toEpochMilli()
            val timeSinceLastRequest = now - lastRequestTime
            val minInterval = 10_000L // 10 seconds

            if (timeSinceLastRequest < minInterval) {
                delay(minInterval - timeSinceLastRequest)
            }
            lastRequestTime = Instant.now().toEpochMilli()
        }

        // In a real implementation, call VRChat’s user API:
        // e.g. client.get("https://api.vrchat.cloud/api/1/users/$userId")
        // For demonstration, we just return mock data
        return VrcUserInfo(
            id = userId,
            displayName = "MockUser-$userId",
            bio = "I am a mock VRChat user",
            bioLinks = "http://example.com/user/$userId"
        )
    }
}

data class VrcUserInfo(
    val id: String,
    val displayName: String?,
    val bio: String?,
    val bioLinks: String?
)