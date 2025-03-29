plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    alias(libs.plugins.kotlinJvm)

    // Apply the Application plugin to add support for building an executable JVM application.
    application
}

dependencies {
    // Project "app" depends on project "utils". (Project paths are separated with ":", so ":utils" refers to the top-level "utils" project.)
    implementation(libs.kotlinxCoroutines)

    implementation(projects.config)
    implementation(projects.vrcApi)
    implementation(projects.openaiApi)
    implementation(projects.database)
    implementation(projects.server)
    implementation(projects.logWatcher)
    implementation(projects.pairScore)

    testImplementation(kotlin("test"))
}

application {
    // Define the Fully Qualified Name for the application main class
    // (Note that Kotlin compiles `App.kt` to a class with FQN `com.example.app.AppKt`.)
    mainClass = "com.kemonotigris.app.AppKt"
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}