package com.kemonotigris

import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.defaultResource
import io.ktor.server.http.content.resources
import io.ktor.server.http.content.static
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Data class for the compatibility view we'll send to the frontend
 */
@Serializable
data class UserCompatibilityView(
    val userId: String,
    val userName: String,
    val compatibilityScore: Int,
    val compatibilityReason: String,
    val suggestedQuestions: List<String>,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val lastUpdated: Long = 0
)

/**
 * Server to display VRChat user compatibility information
 */
class VRChatFriendFinderServer(
    private val usersInInstanceFlow: Flow<Set<String>>,
    private val compatibilityResultsFlow: Flow<Map<String, OpenAiClient.CompatibilityResult>>,
    private val database: Database,
    private val serverPort: Int = 8080
) {
    fun start() {
        embeddedServer(Netty, port = serverPort, host = "0.0.0.0") {
            install(SSE)
            // Set up CORS to allow browser access
            install(CORS) {
                anyHost()
                allowHeader(HttpHeaders.ContentType)
            }

            // Install JSON content negotiation
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                })
            }

            routing {
                // Endpoint to get users currently in the instance with their compatibility data
                get("/api/users") {
                    val currentUsers = usersInInstanceFlow.first()
                    val compatibilityResults = compatibilityResultsFlow.first()

                    // Transform to a list of UserCompatibilityView objects
                    val usersList = currentUsers.mapNotNull { userId ->
                        val userInfo = database.vrchatUserQueries.selectUserById(userId).executeAsOneOrNull()
                        val compatibility = compatibilityResults[userId]

                        if (userInfo != null) {
                            UserCompatibilityView(
                                userId = userId,
                                userName = userInfo.name ?: "unknown",
                                bio = userInfo.bio,
                                avatarUrl = userInfo.avatar_thumbnail_url,
                                compatibilityScore = compatibility?.compatibilityScore ?: 0,
                                compatibilityReason = compatibility?.compatibilityReason ?: "Analyzing...",
                                suggestedQuestions = compatibility?.suggestedQuestions ?: emptyList(),
                                lastUpdated = userInfo.last_updated ?: 0L
                            )
                        } else null
                    }

                    // Use a properly serializable wrapper class
                    call.respond(mapOf("users" to usersList))
                }

                // Endpoint to get all compatibility data regardless of who's in instance
                get("/api/all-users") {
                    val allUsers = getAllUserCompatibilityData()
                    call.respond(mapOf("users" to allUsers))
                }

                sse("/api/updates/current-instance") {
                    // Combine both flows to create a single flow of updates
                    val updatesFlow = combine(
                        usersInInstanceFlow,
                        compatibilityResultsFlow
                    ) { users, compatibility ->
                        val usersList = users.mapNotNull { userId ->
                            val userInfo = database.vrchatUserQueries.selectUserById(userId).executeAsOneOrNull()
                            val compat = compatibility[userId]

                            if (userInfo != null) {
                                UserCompatibilityView(
                                    userId = userId,
                                    userName = userInfo.name ?: "unknown",
                                    bio = userInfo.bio,
                                    avatarUrl = userInfo.avatar_thumbnail_url,
                                    compatibilityScore = compat?.compatibilityScore ?: 0,
                                    compatibilityReason = compat?.compatibilityReason ?: "Analyzing...",
                                    suggestedQuestions = compat?.suggestedQuestions ?: emptyList(),
                                    lastUpdated = userInfo.last_updated ?: 0L
                                )
                            } else null
                        }
                        mapOf("users" to usersList)
                    }

                    // Emit each update as an SSE event
                    updatesFlow.collect { update ->
                        send(Json.encodeToString(update))
                    }
                }

                sse("/api/updates/all-users") {
                    // This will emit whenever compatibility data changes
                    compatibilityResultsFlow.map { _ ->
                        // Get all users from the database
                        val allUsers = getAllUserCompatibilityData()
                        mapOf("users" to allUsers)
                    }.collect { update ->
                        send(Json.encodeToString(update))
                    }
                }

                // Serve static web files
                static("/") {
                    resources("static")
                    defaultResource("static/index.html")
                }
            }
        }.start(wait = true)
    }

    /**
     * Get compatibility data for users currently in the instance
     */
    private fun getUserCompatibilityData(userIds: Set<String>): List<UserCompatibilityView> {
        return userIds.mapNotNull { userId ->
            val analysis = database.compatibilityQueries.selectCompatibilityAnalysisForUser(userId).executeAsOneOrNull()

            analysis?.let {
                // Try to get additional user info from VRChat user table
                val userInfo = database.vrchatUserQueries.selectUserById(userId).executeAsOneOrNull()

                UserCompatibilityView(
                    userId = it.user_id,
                    userName = it.user_name,
                    compatibilityScore = it.compatibility_score.toInt(),
                    compatibilityReason = it.compatibility_reason,
                    suggestedQuestions = it.suggested_questions.split("|||").filter { q -> q.isNotEmpty() },
                    avatarUrl = userInfo?.avatar_thumbnail_url,
                    bio = userInfo?.bio,
                    lastUpdated = it.created_at
                )
            }
        }
    }

    /**
     * Get all compatibility data from the database
     */
    private fun getAllUserCompatibilityData(): List<UserCompatibilityView> {
        return database.compatibilityQueries.selectAllCompatibilityAnalyses().executeAsList().map { analysis ->
            // Try to get additional user info from VRChat user table
            val userInfo = database.vrchatUserQueries.selectUserById(analysis.user_id).executeAsOneOrNull()

            UserCompatibilityView(
                userId = analysis.user_id,
                userName = analysis.user_name,
                compatibilityScore = analysis.compatibility_score.toInt(),
                compatibilityReason = analysis.compatibility_reason,
                suggestedQuestions = analysis.suggested_questions.split("|||").filter { q -> q.isNotEmpty() },
                avatarUrl = userInfo?.avatar_thumbnail_url,
                bio = userInfo?.bio,
                lastUpdated = analysis.created_at
            )
        }
    }
}