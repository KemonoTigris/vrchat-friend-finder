rootProject.name = "vrc-friend-finder"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// The settings file is the entry point of every Gradle build.
// Its primary purpose is to define the subprojects.
// It is also used for some aspects of project-wide configuration, like managing plugins, dependencies, etc.
// https://docs.gradle.org/current/userguide/settings_file_basics.html

// You can configure pluginManagement to pull from the version catalog as well:
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }

}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

plugins {
    // Use the Foojay Toolchains plugin to automatically download JDKs required by subprojects.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

// Include the `app` and `utils` subprojects in the build.
// If there are changes in only one of the projects, Gradle will rebuild only the one that has changed.
// Learn more about structuring projects with Gradle - https://docs.gradle.org/8.7/userguide/multi_project_builds.html
include(":app")

include("log-watcher")
include("config")
include("pair-score")
include("vrc-api")
include("openai-api")
include("database")
include("server")
