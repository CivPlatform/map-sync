// Update Gradle Wrapper using: ./gradlew wrapper --distribution-type bin --gradle-version <version>
// See Gradle's releases here: https://gradle.org/releases/

pluginManagement {
	repositories {
		maven(url = "https://maven.fabricmc.net/")
		mavenCentral()
		gradlePluginPortal()
	}
}

rootProject.name = "MapSync"
