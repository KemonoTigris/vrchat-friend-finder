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

    fun getLatestLogFile(dir: File): File? {
        // Define a regex pattern for log file names in the expected format.
        val logFileRegex = Regex("output_log_(\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2})\\.txt")
        // Define a formatter matching the date-time part in the filename.
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

        return dir.listFiles { file ->
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
    }

    private suspend fun watchLogFile() {
        println(properties.getProperty("directory.vrchatlogs"))
        val dir = File(properties.getProperty("directory.vrchatlogs"))

        // Get the latest log file based on the timestamp in its name.
        val file = getLatestLogFile(dir) ?: run {
            println("No log file found in directory: ${dir.absolutePath}")
            return
        }

        var lastPosition = if (file.exists()) file.length() else 0L

        try {
            FileSystems.getDefault().newWatchService().use { watchService ->
                dir.toPath().register(watchService, ENTRY_MODIFY)
                while (watchJob.isActive) {
                    // Process existing content first
                    if (file.exists() && file.length() > lastPosition) {
                        processNewContent(file, lastPosition)
                        lastPosition = file.length()
                    }

                    // Wait for next change
                    val key = watchService.poll(5, java.util.concurrent.TimeUnit.SECONDS)
                    key?.pollEvents()?.forEach { event ->
                        if (event.kind() != OVERFLOW &&
                            (event as WatchEvent<Path>).context().fileName.toString() == file.name
                        ) {
                            if (file.exists() && file.length() > lastPosition) {
                                processNewContent(file, lastPosition)
                                lastPosition = file.length()
                            }
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
        try {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(fromPosition)
                var line: String? = raf.readLine()
                while (line != null) {
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
                    currentUsers.add(userId)
                }
            }

            line.contains("OnPlayerLeft") -> {
                val userId = extractUserId(line)
                if (userId.isNotBlank()) {
                    currentUsers.remove(userId)
                }
            }
        }
    }

    private fun extractUserId(line: String): String {
        // Example line:
        // 2025.03.20 20:26:58 Debug - [Behaviour] OnPlayerJoined username (usr_bbe1cab4-63bf-4d72-9841-661cd72685e6)
        val match = userIdRegex.find(line)
        return match?.groupValues?.getOrNull(1) ?: ""
    }

    fun stop() {
        watchJob.cancel()
        ioScope.cancel()
    }
}