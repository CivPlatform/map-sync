// Update Gradle Wrapper using: ./gradlew wrapper --distribution-type bin --gradle-version <version>
// See Gradle's releases here: https://gradle.org/releases/

pluginManagement {
	repositories {
		maven(url = "https://maven.fabricmc.net/")
		mavenCentral()
		gradlePluginPortal()
	}
}

plugins {
	// Lets Gradle auto-provision the Java 25 toolchain required by Minecraft 26.1+.
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "MapSync"

include(":dep-websockets")
