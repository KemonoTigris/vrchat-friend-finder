package com.kemonotigris.app

import com.kemonotigris.LogWatcher

fun main() {
    val logWatcher = LogWatcher()
    println("App is running. Press Enter to exit...")
    readLine()  // Blocks until the user presses Enter
    logWatcher.stop()
}
