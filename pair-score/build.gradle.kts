plugins {
    alias(libs.plugins.kotlinJvm)
}

group = "com.kemonotigris"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlinxCoroutines)

    implementation(projects.config)
    implementation(projects.vrcApi)
    implementation(projects.openaiApi)
    implementation(projects.database)
    implementation(projects.server)
    implementation(projects.logWatcher)

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}