plugins {
	id("java-library")
	alias(libs.plugins.shadow)
}

version = libs.java.ws.get().version!!

dependencies {
	implementation(libs.java.ws)
}

repositories {
	mavenCentral()
}

tasks {
	shadowJar {
		include("org/java_websocket/**")
		include("META-INF/LICENSE.txt")
		rename("LICENSE.txt", "LICENSE_JavaWebSockets")
		relocate(
			"org.java_websocket",
			"gjum.minecraft.mapsync.mod.deps.websockets"
		)
	}
}

val shadedElements by configurations.creating {
	isCanBeConsumed = true
	isCanBeResolved = false
}

artifacts {
	add(shadedElements.name, tasks.shadowJar)
}
