plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinPluginSerialization)
    alias(libs.plugins.ktorServerPlugin)
}

group = "com.kemonotigris"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    // Serialization
    implementation(libs.kotlinxSerialization)

    implementation(libs.ktorServerCore)
    implementation(libs.ktorServerNetty)
    implementation(libs.ktorServerCors)
    implementation(libs.ktorServerSse)
    implementation(libs.ktorServerResources)
    implementation(libs.ktorServerContentNegotiation)
    implementation(libs.ktorServerJsonSerialization)

    implementation(projects.database)
    implementation(projects.openaiApi)

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}