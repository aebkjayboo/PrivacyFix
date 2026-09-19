pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("fabric-loom") version (extra["loom_version"] as String)
    }
}

rootProject.name = "sais-privacyfix"
include("v1_21_11", "v26_2", "probe_1_21_11", "probe_26_2")
