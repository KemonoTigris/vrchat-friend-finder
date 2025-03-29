package com.kemonotigris

import java.text.SimpleDateFormat
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Helper function to format a timestamp (in milliseconds) to a human-readable date string
 */
fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    return formatter.format(date)
}

suspend fun main() {
    val config = loadProperties()
    val database = DatabaseFactory.getDatabase()
    val vrcApiClient = VrcApiClient(
        username = config.getProperty("vrc.username"),
        password = config.getProperty("vrc.password")
    )

    // Create OpenAI client
    val openAiClient = OpenAiClient(
        apiKey = config.getProperty("openai.apikey")
    )

    // Create a flow with a single test user
    val testUserId = "usr_889bfa25-cdff-498f-a175-95aa9d87e9d8"
    val usersInInstanceFlow = MutableStateFlow(setOf(testUserId))


    try {
        val userId = "usr_889bfa25-cdff-498f-a175-95aa9d87e9d8"

        // Check if user exists in database
        val existingUser = database.vrchatUserQueries.selectUserById(userId).executeAsOneOrNull()

        println("User exists in VRChat user table: ${existingUser != null}")

        // Get user info either from database or API
        val userInfo = if (existingUser == null) {
            // Fetch from API and insert into database
            val apiUserInfo = vrcApiClient.getUserInfo(userId)

            // Insert user into database
            val bioLinksString = apiUserInfo.bioLinks?.joinToString(",") ?: ""
            val isFriendLong = if (apiUserInfo.isFriend) 1L else 0L

            database.vrchatUserQueries.insertOrReplaceUser(
                apiUserInfo.id,
                apiUserInfo.displayName ?: "Unknown",  // Handle potential null
                apiUserInfo.bio ?: "",                 // Handle potential null
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

        // Information about myself to compare with the VRChat user
        val myInfo = """
            I'm a tech enthusiast passionate about AI and VR technologies. I enjoy deep conversations
            about technology, innovation, and how they impact society. I'm interested in machine learning,
            programming, and creating digital experiences. I'm also into gaming and exploring the creative
            potential of virtual worlds.
        """.trimIndent()

        // Check if we already have a compatibility analysis for this user
        val existingAnalysis = database.compatibilityQueries
            .selectCompatibilityAnalysisForUser(userId)
            .executeAsOneOrNull()

        val compatibilityResult = if (existingAnalysis != null) {
            println("Found existing compatibility analysis from ${formatTimestamp(existingAnalysis.created_at)}")

            // Parse the stored questions from string to list (now type-safe)
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
                user_name = userInfo.displayName ?: "Unknown",  // Safely handle null
                compatibility_score = newAnalysis.compatibilityScore.toLong(),
                compatibility_reason = newAnalysis.compatibilityReason,
                suggested_questions = questionsString,
                created_at = System.currentTimeMillis()
            )

            println("Compatibility analysis stored in database")

            newAnalysis
        }

        // Display compatibility results
        println("\n===== COMPATIBILITY ANALYSIS =====")
        println("User: ${userInfo.displayName ?: "Unknown"}")
        println("Compatibility Score: ${compatibilityResult.compatibilityScore}/100")
        println("\nReasoning:")
        println(compatibilityResult.compatibilityReason)
        println("\nSuggested Questions:")
        compatibilityResult.suggestedQuestions.forEachIndexed { index, question ->
            println("${index + 1}. $question")
        }
        println("==================================")

        // Get top compatible users
        val topUsers = database.compatibilityQueries
            .selectTopCompatibilityScores(5)
            .executeAsList()

        if (topUsers.isNotEmpty()) {
            println("\n===== TOP COMPATIBLE USERS =====")
            topUsers.forEach { analysis ->
                println("${analysis.user_name} - Score: ${analysis.compatibility_score}/100")

                // Get the first suggested question (now type-safe)
                val firstQuestion = analysis.suggested_questions
                    .split("|||")
                    .firstOrNull()

                println("   Top Question: ${firstQuestion ?: "None"}")
                println("   Analyzed: ${formatTimestamp(analysis.created_at)}")
                println()
            }
            println("==================================")
        }

    } catch (e: Exception) {
        println("Error: ${e.message}")
        e.printStackTrace()
    } finally {
        // Clean up resources
        openAiClient.close()
    }

    // Then start the server
    val server = VRChatFriendFinderServer(
        usersInInstanceFlow = usersInInstanceFlow,
        database = database,
        serverPort = 8080
    )

    println("Starting VRChat Friend Finder web server on http://localhost:8080")
    server.start()
}