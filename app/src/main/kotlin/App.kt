package com.kemonotigris.app

import com.kemonotigris.CompatibilityService
import com.kemonotigris.DatabaseFactory
import com.kemonotigris.LogWatcher
import com.kemonotigris.OpenAiClient
import com.kemonotigris.VRChatFriendFinderServer
import com.kemonotigris.VrcApiClient
import com.kemonotigris.loadProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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

    // My personal bio for compatibility matching
    val myInfo = """
        I'm a tech enthusiast passionate about AI and VR technologies. I enjoy deep conversations
        about technology, innovation, and how they impact society. I'm interested in machine learning,
        programming, and creating digital experiences. I'm also into gaming and exploring the creative
        potential of virtual worlds.
    """.trimIndent()

    // Create the compatibility service
    val compatibilityService = CompatibilityService(
        database = database,
        vrcApiClient = vrcApiClient,
        openAiClient = openAiClient,
        myInfo = myInfo
    )

    // Create the log watcher to monitor for users
    val logWatcher = LogWatcher()

    // Launch a coroutine to process users as they join/leave
    val userProcessingJob = CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
        var previousUsers = emptySet<String>()

        logWatcher.usersInInstanceFlow.collect { currentUsers ->
            println("User state changed: ${currentUsers.size} users in instance")

            // Process new users
            currentUsers.forEach { userId ->
                if (userId !in previousUsers) {
                    println("Processing new user: $userId")
                    compatibilityService.processUser(userId)
                }
            }

            // Handle users who left
            val departedUsers = previousUsers - currentUsers
            departedUsers.forEach { userId ->
                println("User left instance: $userId")
                compatibilityService.removeUser(userId)
            }

            previousUsers = currentUsers
        }
    }

    // Start the web server with the compatibility results flow
    val server = VRChatFriendFinderServer(
        usersInInstanceFlow = logWatcher.usersInInstanceFlow,
        compatibilityResultsFlow = compatibilityService.resultsFlow,
        database = database,
        serverPort = 8080
    )

    println("Starting VRChat Friend Finder web server on http://localhost:8080")
    // Start the server in a separate coroutine so it doesn't block
    val serverJob = CoroutineScope(Dispatchers.IO).launch {
        server.start()
    }
    // Give the server a moment to initialize??
    delay(2000)

    // Keep the application running
    try {
        // Wait indefinitely (the server and processing are happening in background)
        runBlocking {
            delay(Long.MAX_VALUE)
        }
    } finally {
        // Cleanup when the app is terminated
        userProcessingJob.cancel()
        serverJob.cancel()
        logWatcher.stop()
        openAiClient.close()
    }
}
