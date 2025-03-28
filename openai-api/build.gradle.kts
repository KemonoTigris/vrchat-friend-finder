plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinPluginSerialization)
}

group = "com.kemonotigris"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    // Ktor client
    implementation(libs.ktorClientCore)
    implementation(libs.ktorClientCio)
    implementation(libs.ktorClientContentNegotiation)
    implementation(libs.ktorClientAuth)
    implementation(projects.vrcApi)
    implementation(projects.database)

    implementation(libs.ktorClientLogging)


    // Coroutines
    implementation(libs.kotlinxCoroutines)

    // Serialization
    implementation(libs.kotlinxSerialization)
    // logging
    implementation(libs.logbackClassic)

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}