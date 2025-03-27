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
    implementation("io.ktor:ktor-client-logging:3.1.1")


    // Coroutines
    implementation(libs.kotlinxCoroutines)

    // Serialization
    implementation(libs.kotlinxSerialization)
    implementation("ch.qos.logback:logback-classic:1.4.12")



    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}