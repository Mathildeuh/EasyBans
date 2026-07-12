plugins {
    // Lets Gradle auto-provision a JDK 25 toolchain if one isn't already installed locally.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "EasyBans"
