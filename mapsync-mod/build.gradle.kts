plugins {
	alias(libs.plugins.fabricLoom)
}

private val mod_name = project.property("mod_name").toString()
version = "${project.property("mod_version")}-${libs.versions.minecraft.get()}"

private val modLocalDep = configurations.create("modLocalDep")

base {
	archivesName = project.property("archives_base_name").toString()
}

loom {
	runConfigs.configureEach {
		programArgs += buildList {
			// Use same username between runClient runs
			addAll(listOf("--username", "LocalModTester"))
		}
	}
}

dependencies {
	minecraft(libs.minecraft)
	loom {
		mappings(layered {
			officialMojangMappings()
			parchment(libs.parchment)
		})
	}
	modImplementation(libs.fabricLoader)
	modImplementation(libs.fabricApi)

	project(":dep-websockets", configuration = "shadedElements").also {
		implementation(it)
		include(it)
	}

	modLocalDep(libs.fixChat)
	modImplementation(libs.modmenu)

	libs.voxelmap.also {
		modCompileOnly(it)
		// modLocalDep(it) // Uncomment to test VoxelMap
	}
	libs.journeymap.also {
		modCompileOnly(it)
		modLocalDep(it) // Uncomment to test JourneyMap
	}
	libs.xaerosmap.also {
		modCompileOnly(it)
		//modLocalDep(it) // Uncomment to test XaerosMap
	}
}

repositories {
	maven(url = "https://maven.parchmentmc.org") {
		name = "ParchmentMC"
	}
	maven(url = "https://api.modrinth.com/maven") {
		name = "Modrinth"
		content {
			includeGroup("maven.modrinth")
		}
	}
	mavenCentral()
}

java {
	withSourcesJar()
	sourceCompatibility = JavaVersion.VERSION_21
	targetCompatibility = JavaVersion.VERSION_21
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

tasks {
	compileJava {
		options.encoding = "UTF-8"
		options.release = 21
	}
	jar {
		from(file("../LICENSE")) {
			rename { "LICENSE_${mod_name}" }
		}
	}
	processResources {
		val expansions: Map<String, Any> = buildMap {
			put("mod_name", mod_name)
			put("mod_version", project.version)
			putAll(listOf(
				"mod_description",
				"copyright_licence",
				"mod_home_url",
				"mod_source_url",
				"mod_issues_url"
			).associateWith { project.property(it).toString() })
			put("minecraft_version", libs.versions.minecraft.get())
			put("fabric_loader_version", libs.versions.fabricLoader.get())
		}
		inputs.properties(expansions)
		filesMatching("fabric.mod.json") {
			expand(expansions)
			filter {
				it.replace(
					"\"%FABRIC_AUTHORS_ARRAY%\"",
					groovy.json.JsonBuilder(project.property("mod_authors").toString().split(",")).toString()
				)
			}
		}
		filesMatching("assets/mapsync/lang/en_us.json") {
			expand(expansions)
		}
		filesMatching("mapsync.version.const") {
			expand(expansions)
		}
	}
	val copyRunClientDeps = register<Sync>("copyMapSyncRunClientDependencies") {
		from(modLocalDep)
		into(file("run/mods/"))
	}
	runClient {
		dependsOn(copyRunClientDeps)
	}
	val copyDistJar = register<Sync>("distJar") {
		from(remapJar)
		into(file("dist/"))
	}
	build {
		dependsOn(copyDistJar)
	}
}
