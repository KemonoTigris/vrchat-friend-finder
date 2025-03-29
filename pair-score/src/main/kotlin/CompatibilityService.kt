package com.kemonotigris

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class CompatibilityService(
    private val database: Database,
    private val vrcApiClient: VrcApiClient,
    private val openAiClient: OpenAiClient,
    private val myInfo: String
) {
    // Map to track processed users and their compatibility results
    private val compatibilityResults = ConcurrentHashMap<String, OpenAiClient.CompatibilityResult>()

    // Flow to expose current compatibility results to the UI
    private val _resultsFlow = MutableStateFlow<Map<String, OpenAiClient.CompatibilityResult>>(emptyMap())
    val resultsFlow = _resultsFlow.asStateFlow()

    suspend fun processUser(userId: String) {
        // Skip if we've already processed this user
        if (compatibilityResults.containsKey(userId)) return

        try {
            // Get user info (from DB or VRC API)
            val userInfo = getUserInfo(userId)

            // Get or generate compatibility analysis
            val compatibilityResult = analyzeCompatibility(userInfo)

            // Store the result
            compatibilityResults[userId] = compatibilityResult
            _resultsFlow.value = compatibilityResults.toMap()

            println("Processed user: ${userInfo.displayName} (Score: ${compatibilityResult.compatibilityScore})")
        } catch (e: Exception) {
            println("Error processing user $userId: ${e.message}")
        }
    }

    private suspend fun getUserInfo(userId: String): VrcUserInfo {
        // Check if user exists in database
        val existingUser = database.vrchatUserQueries.selectUserById(userId).executeAsOneOrNull()

        return if (existingUser == null) {
            // Fetch from API and insert into database
            val apiUserInfo = vrcApiClient.getUserInfo(userId)

            // Insert user into database
            val bioLinksString = apiUserInfo.bioLinks?.joinToString(",") ?: ""
            val isFriendLong = if (apiUserInfo.isFriend) 1L else 0L

            database.vrchatUserQueries.insertOrReplaceUser(
                apiUserInfo.id,
                apiUserInfo.displayName ?: "Unknown",
                apiUserInfo.bio ?: "",
                bioLinksString,
                apiUserInfo.currentAvatarImageUrl ?: "",
                apiUserInfo.currentAvatarThumbnailImageUrl ?: "",
                apiUserInfo.statusDescription ?: "",
                isFriendLong,
                apiUserInfo.lastLogin ?: "",
                System.currentTimeMillis()
            )

            apiUserInfo
        } else {
            // Convert database user to VrcUserInfo
            VrcUserInfo(
                id = existingUser.id,
                displayName = existingUser.name,
                bio = existingUser.bio,
                bioLinks = existingUser.bioLinks?.split(",")?.filter { it.isNotEmpty() },
                currentAvatarImageUrl = existingUser.avatar_image_url,
                currentAvatarThumbnailImageUrl = existingUser.avatar_thumbnail_url,
                statusDescription = existingUser.statusDescription,
                isFriend = existingUser.is_friend == 1L,
                lastLogin = existingUser.last_login
            )
        }
    }

    private suspend fun analyzeCompatibility(userInfo: VrcUserInfo): OpenAiClient.CompatibilityResult {
        // Check if we already have a compatibility analysis for this user
        val existingAnalysis = database.compatibilityQueries
            .selectCompatibilityAnalysisForUser(userInfo.id)
            .executeAsOneOrNull()

        return if (existingAnalysis != null) {
            println("Found existing compatibility analysis from ${formatTimestamp(existingAnalysis.created_at)}")

            // Parse the stored questions from string to list
            val questions = existingAnalysis.suggested_questions
                .split("|||")
                .filter { it.isNotEmpty() }

            OpenAiClient.CompatibilityResult(
                compatibilityScore = existingAnalysis.compatibility_score.toInt(),
                compatibilityReason = existingAnalysis.compatibility_reason,
                suggestedQuestions = questions
            )
        } else {
            println("No existing analysis found, querying OpenAI...")

            // Query OpenAI for compatibility analysis
            val newAnalysis = openAiClient.analyzeCompatibility(myInfo, userInfo)

            // Store the result in the database
            val questionsString = newAnalysis.suggestedQuestions.joinToString("|||")

            database.compatibilityQueries.insertOrReplaceCompatibilityAnalysis(
                user_id = userInfo.id,
                user_name = userInfo.displayName ?: "Unknown",
                compatibility_score = newAnalysis.compatibilityScore.toLong(),
                compatibility_reason = newAnalysis.compatibilityReason,
                suggested_questions = questionsString,
                created_at = System.currentTimeMillis()
            )

            newAnalysis
        }
    }

    fun removeUser(userId: String) {
        compatibilityResults.remove(userId)
        _resultsFlow.value = compatibilityResults.toMap()
    }
}
