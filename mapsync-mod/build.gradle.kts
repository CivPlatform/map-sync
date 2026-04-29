plugins {
	alias(libs.plugins.fabricLoom)
}

// gradle.properties
private val project_name: String by project
private val project_group: String by project
private val mapsync_version: String by project
private val project_description: String by project
private val project_authors: String by project
private val project_copyright: String by project
private val project_home_url: String by project
private val project_source_url: String by project
private val project_issues_url: String by project

version = "${mapsync_version}-${libs.versions.minecraft.get()}"
group = project_group

private val modLocalDep: Configuration by configurations.creating

base {
	archivesName = project_name
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
		modLocalDep(it) // Uncomment to test VoxelMap
	}
	libs.journeymap.also {
		modCompileOnly(it)
		//modLocalDep(it) // Uncomment to test JourneyMap
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
			rename { "LICENSE_${project_name}" }
		}
	}
	processResources {
		val expansions: Map<String, Any> = buildMap expansions@{
			this@expansions["mod_name"] = project_name
			this@expansions["mod_version"] = project.version
			this@expansions["mod_description"] = project_description
			this@expansions["mod_copyright"] = project_copyright
			this@expansions["mod_home_url"] = project_home_url
			this@expansions["mod_source_url"] = project_source_url
			this@expansions["mod_issues_url"] = project_issues_url
			this@expansions["minecraft_version"] = libs.versions.minecraft.get()
			this@expansions["fabric_loader_version"] = libs.versions.fabricLoader.get()
		}
		inputs.properties(expansions)
		filesMatching("fabric.mod.json") {
			expand(expansions)
			filter {
				it.replace(
					"\"%FABRIC_AUTHORS_ARRAY%\"",
					groovy.json.JsonBuilder(project_authors.split(",")).toString()
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
