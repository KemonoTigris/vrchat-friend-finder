plugins {
    alias(libs.plugins.kotlinJvm)
}

group = "com.kemonotigris"

repositories {
    mavenCentral()
}

dependencies {
    implementation(projects.config)
    implementation(libs.kotlinxCoroutines)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}