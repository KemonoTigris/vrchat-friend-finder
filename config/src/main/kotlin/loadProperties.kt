package com.kemonotigris

import java.io.File
import java.util.*

fun loadProperties(): Properties {
    val properties = Properties()
    // Get the working directory. This is typically your project's root when running from an IDE.
    val projectRoot = System.getProperty("user.dir")
    val file = File(projectRoot, "local.properties")
    if (file.exists()) {
        file.inputStream().use { input ->
            properties.load(input)
        }
    } else {
        println("local.properties file not found at ${file.absolutePath}")
    }
    return properties
}