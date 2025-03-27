package com.kemonotigris

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
suspend fun main() {
    val config = loadProperties()
    val database = DatabaseFactory.getDatabase()
    val vrcApiClient = VrcApiClient(
        username = config.getProperty("vrc.username"),
        password = config.getProperty("vrc.password")
    )

    val userId = "usr_889bfa25-cdff-498f-a175-95aa9d87e9d8"

    // Check if user exists in database
    val existingUser = database.vrchatUserQueries.selectUserById(userId).executeAsOneOrNull()

    println("Database query executed. User exists: ${existingUser != null}")

    if (existingUser == null) {
        println("User doesn't exist in DB, fetching from API...")
        val userInfo = vrcApiClient.getUserInfo(userId)
        println("API returned user: $userInfo")

        val bioLinksString = userInfo.bioLinks?.joinToString(",") ?: ""
        val isFriendLong = if (userInfo.isFriend) 1L else 0L

        try {
            database.vrchatUserQueries.insertOrReplaceUser(
                userInfo.id,
                userInfo.displayName,
                userInfo.bio,
                bioLinksString,
                userInfo.currentAvatarImageUrl,
                userInfo.currentAvatarThumbnailImageUrl,
                userInfo.status,
                isFriendLong,
                userInfo.lastLogin ?: "", // Handle null lastLogin
                System.currentTimeMillis()
            )
            println("User successfully inserted into database")
        } catch (e: Exception) {
            println("Error inserting user into database: ${e.message}")
            e.printStackTrace()
        }
    } else {
        println("User found in database:")
        println("- ID: ${existingUser.id}")
        println("- Name: ${existingUser.name}")
        println("- Bio: ${existingUser.bio}")
        println("- BioLinks: ${existingUser.bioLinks}")
        println("- Avatar URL: ${existingUser.avatar_image_url}")
        println("- Status: ${existingUser.status}")
        println("- Is Friend: ${existingUser.is_friend}")
        println("- Last Login: ${existingUser.last_login}")
        println("- Last Updated: ${existingUser.last_updated}")
    }

    // Query again to verify
    val userDetails = database.vrchatUserQueries.selectUserById(userId).executeAsOneOrNull()

    if (userDetails != null) {
        println("\nFinal query result:")
        println("- ID: ${userDetails.id}")
        println("- Name: ${userDetails.name}")
        println("- Bio: ${userDetails.bio}")
        println("- BioLinks: ${userDetails.bioLinks}")
        println("- Avatar URL: ${userDetails.avatar_image_url}")
        println("- Status: ${userDetails.status}")
        println("- Is Friend: ${userDetails.is_friend}")
        println("- Last Login: ${userDetails.last_login}")
        println("- Last Updated: ${userDetails.last_updated}")
    } else {
        println("Final query returned null")
    }
}