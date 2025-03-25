package com.kemonotigris

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.StandardWatchEventKinds.OVERFLOW
import java.nio.file.WatchEvent
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

class LogWatcher() {
    private val properties = loadProperties()
    private val _usersInInstanceFlow = MutableStateFlow<Set<String>>(emptySet())
    val usersInInstanceFlow: Flow<Set<String>> = _usersInInstanceFlow.asStateFlow()

    private val currentUsers = ConcurrentHashMap.newKeySet<String>()
    private val userIdRegex = "\\((usr_[a-z0-9\\-]+)\\)".toRegex(RegexOption.IGNORE_CASE)

    private val watchJob: Job
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        watchJob = ioScope.launch {
            watchLogFile()
        }
    }

    private fun getLatestLogFile(dir: File): File? {
        println("Looking for latest log file in directory: ${dir.absolutePath}")
        // Define a regex pattern for log file names in the expected format.
        val logFileRegex = Regex("output_log_(\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2})\\.txt")
        // Define a formatter matching the date-time part in the filename.
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

        val latestFile = dir.listFiles { file ->
            file.isFile && file.name.matches(logFileRegex)
        }?.maxByOrNull { file ->
            // Extract the timestamp part.
            val match = logFileRegex.find(file.name)
            val dateTimeString = match?.groupValues?.get(1)
            // If the timestamp can be parsed, use it; otherwise use a minimum value.
            if (dateTimeString != null) {
                LocalDateTime.parse(dateTimeString, formatter)
            } else {
                LocalDateTime.MIN
            }
        }
        if (latestFile != null) {
            println("Latest log file found: ${latestFile.name}")
        } else {
            println("No log file found in directory: ${dir.absolutePath}")
        }
        return latestFile
    }

    private suspend fun watchLogFile() {
        val directoryPath = properties.getProperty("directory.vrchatlogs")
        println("Starting to watch log files in directory: $directoryPath")
        val dir = File(directoryPath)

        // Identify our initial "current" file and the last position
        var currentFile: File? = getLatestLogFile(dir)
        var lastPosition = currentFile?.let { if (it.exists()) it.length() else 0L } ?: 0L
        println("Initial file: '${currentFile?.name}' with size: $lastPosition bytes")

        try {
            FileSystems.getDefault().newWatchService().use { watchService ->
                dir.toPath().register(watchService, ENTRY_MODIFY)
                println("WatchService registered for directory: ${dir.absolutePath}")

                while (watchJob.isActive) {
                    // Always re-check if there's a newer log file
                    val latestFile = getLatestLogFile(dir)

                    if (latestFile != null && latestFile != currentFile) {
                        // Switch to the new log file
                        println("Switching to a new log file: ${latestFile.name}")
                        currentFile = latestFile
                        // Decide whether to read from the start or skip to end:
                        // lastPosition = 0L     // to read the entire new file from the beginning
                        lastPosition = currentFile!!.length() // to only read new lines in the new file
                    }

                    // If we have a current file, check for new data
                    if (currentFile != null && currentFile!!.exists()) {
                        if (currentFile!!.length() > lastPosition) {
                            println(
                                "Detected new content in '${currentFile!!.name}' from " +
                                        "position $lastPosition to ${currentFile!!.length()}"
                            )
                            processNewContent(currentFile!!, lastPosition)
                            lastPosition = currentFile!!.length()
                        }
                    }

                    // Wait/poll for filesystem events
                    println("Polling for changes...")
                    val key = watchService.poll(5, java.util.concurrent.TimeUnit.SECONDS)
                    key?.pollEvents()?.forEach { event ->
                        if (event.kind() != OVERFLOW) {
                            // We only care about modifies, but you could refine event checks
                            val changedFile = (event as WatchEvent<Path>).context().fileName.toString()
                            println("Received ${event.kind()} event: $changedFile")

                            // If the event refers to the file we’re currently reading,
                            // or if a brand new file is the newest, we’ll handle it
                            // on the *next* loop iteration (because we do getLatestLogFile above).
                        }
                    }
                    key?.reset()
                }
            }
        } catch (e: Exception) {
            println("error watching log file: ${e.message}")
        }
    }

    private fun processNewContent(file: File, fromPosition: Long) {
        println("Processing new content in file '${file.name}' starting at position $fromPosition")
        try {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(fromPosition)
                var line: String? = raf.readLine()
                while (line != null) {
                    println("Processing line: $line")
                    processLine(line)
                    line = raf.readLine()
                }
            }
        } catch (e: Exception) {
            println("Error reading log file: ${e.message}")
        }
    }

    private fun processLine(line: String) {
        when {
            line.contains("OnPlayerJoined") -> {
                val userId = extractUserId(line)
                if (userId.isNotBlank()) {
                    println("User joined detected: $userId")
                    currentUsers.add(userId)
                    println("Current users count: ${currentUsers.size}")
                }
            }

            line.contains("OnPlayerLeft") -> {
                val userId = extractUserId(line)
                if (userId.isNotBlank()) {
                    println("User left detected: $userId")
                    currentUsers.remove(userId)
                    println("Current users count: ${currentUsers.size}")
                }
            }
        }
    }

    private fun extractUserId(line: String): String {
        // Example line:
        // 2025.03.20 20:26:58 Debug - [Behaviour] OnPlayerJoined username (usr_bbe1cab4-63bf-4d72-9841-661cd72685e6)
        val match = userIdRegex.find(line)
        val userId = match?.groupValues?.getOrNull(1) ?: ""
        println("Extracted userId: '$userId' from line: $line")
        return userId
    }

    fun stop() {
        println("Stopping LogWatcher...")

        watchJob.cancel()
        ioScope.cancel()
    }
}