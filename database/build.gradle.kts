plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.sqlDelight)
}

group = "com.kemonotigris"
version = "unspecified"

repositories {
    mavenCentral()
}

sqldelight {
    databases {
        create("VRChatDatabase") {
            packageName = "com.kemonotigris"
        }
    }
}

dependencies {
    implementation(libs.sqlDelight)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}