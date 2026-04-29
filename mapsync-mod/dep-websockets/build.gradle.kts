plugins {
	id("java-library")
	alias(libs.plugins.shadow)
}

group = libs.java.ws.get().group
version = libs.java.ws.get().version!!

dependencies {
	implementation(libs.java.ws)
}

repositories {
	mavenCentral()
}

tasks {
	shadowJar {
		// Remove slf4j code
		exclude("org/slf4j/**", "META-INF/maven/org.slf4j/**")
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
