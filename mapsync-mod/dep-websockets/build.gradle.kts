plugins {
	id("java-library")
	alias(libs.plugins.shadow)
}

// https://mvnrepository.com/artifact/org.java-websocket/Java-WebSocket
group = "org.java-websocket"
version = "1.6.0"

dependencies {
	implementation("${project.group}:Java-WebSocket:${project.version}")
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

private val shadedElements = configurations.create("shadedElements") {
	isCanBeConsumed = true
	isCanBeResolved = false
}

artifacts {
	add(shadedElements.name, tasks.shadowJar)
}
