package com.kemonotigris

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

    try {
        val userId = "usr_889bfa25-cdff-498f-a175-95aa9d87e9d8"

        // Check if user exists in database
        val existingUser = database.vrchatUserQueries.selectUserById(userId).executeAsOneOrNull()

        println("Database query executed. User exists: ${existingUser != null}")

        // Get user info either from database or API
        val userInfo = if (existingUser == null) {
            // Fetch from API and insert into database (your existing code)
            vrcApiClient.getUserInfo(userId)
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

        // Analyze compatibility
        val compatibilityResult = openAiClient.analyzeCompatibility(myInfo, userInfo)

        println("\n===== COMPATIBILITY ANALYSIS =====")
        println("Compatibility Score: ${compatibilityResult.compatibilityScore}/100")
        println("\nReasoning:")
        println(compatibilityResult.compatibilityReason)
        println("\nSuggested Questions:")
        compatibilityResult.suggestedQuestions.forEachIndexed { index, question ->
            println("${index + 1}. $question")
        }
        println("==================================")
    } catch (e: Exception) {
        println("Error analyzing compatibility: ${e.message}")
        e.printStackTrace()
    } finally {
        // Clean up resources
        openAiClient.close()
    }
}