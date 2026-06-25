plugins {
	alias(libs.plugins.fabricLoom)
}

// gradle.properties
private val projectName = providers.gradleProperty("project_name").get()
private val projectGroup = providers.gradleProperty("project_group").get()
private val projectVersion = providers.gradleProperty("mapsync_version").get()
private val projectDescription = providers.gradleProperty("project_description").get()
private val projectAuthors = providers.gradleProperty("project_authors").get().split(',')
private val projectCopyright = providers.gradleProperty("project_copyright").get()
private val projectHomeUrl = providers.gradleProperty("project_home_url").get()
private val projectSourceUrl = providers.gradleProperty("project_source_url").get()
private val projectIssuesUrl = providers.gradleProperty("project_issues_url").get()

version = "${projectVersion}-${libs.versions.minecraft.get()}"
group = projectGroup

private val modLocalDep: Configuration = configurations.create("modLocalDep")

base {
	archivesName = projectName
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
			rename { "LICENSE_${projectName}" }
		}
	}
	processResources {
		val expansions: Map<String, Any> = buildMap expansions@{
			this@expansions["mod_name"] = projectName
			this@expansions["mod_version"] = project.version
			this@expansions["mod_description"] = projectDescription
			this@expansions["mod_copyright"] = projectCopyright
			this@expansions["mod_home_url"] = projectHomeUrl
			this@expansions["mod_source_url"] = projectSourceUrl
			this@expansions["mod_issues_url"] = projectIssuesUrl
			this@expansions["minecraft_version"] = libs.versions.minecraft.get()
			this@expansions["fabric_loader_version"] = libs.versions.fabricLoader.get()
		}
		inputs.properties(expansions)
		filesMatching("fabric.mod.json") {
			expand(expansions)
			filter {
				it.replace(
					"\"%FABRIC_AUTHORS_ARRAY%\"",
					groovy.json.JsonBuilder(projectAuthors).toString()
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
