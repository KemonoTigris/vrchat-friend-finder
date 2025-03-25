plugins {
    kotlin("jvm")
}

group = "com.kemonotigris"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":config"))
    implementation(libs.kotlinxCoroutines)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}