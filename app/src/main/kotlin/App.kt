package com.kemonotigris.app

import com.kemonotigris.LogWatcher

fun main() {
    val logWatcher = LogWatcher()
    val usersInInstanceFlow = logWatcher.usersInInstanceFlow


    println("App is running. Press Enter to exit...")
    readLine()  // Blocks until the user presses Enter
    logWatcher.stop()
}
