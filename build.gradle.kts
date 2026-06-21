import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("net.fabricmc.fabric-loom")
	`maven-publish`
	id("org.jetbrains.kotlin.jvm") version "2.4.0"
}

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()

repositories {
	// Add repositories to retrieve artifacts from in here.
	// You should only use this when depending on other mods because
	// Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
	// See https://docs.gradle.org/current/userguide/declaring_repositories.html
	// for more information about repositories.
}

loom {
	splitEnvironmentSourceSets()

	mods {
		register("paintandseek") {
			sourceSet(sourceSets.main.get())
			sourceSet(sourceSets.getByName("client"))
		}
	}
}

fabricApi {
	configureDataGeneration {
		client = true
	}
}

dependencies {
	// To change the versions see the gradle.properties file
	minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
	
	implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")

	// Fabric API. This is technically optional, but you probably want it anyway.
	implementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")
	implementation("net.fabricmc:fabric-language-kotlin:${providers.gradleProperty("fabric_kotlin_version").get()}")
}

tasks.processResources {
	val version = version
	inputs.property("version", version)

	filesMatching("fabric.mod.json") {
		expand("version" to version)
	}
}

// Mirror the datapack functions into the standalone example datapack so the two
// copies don't drift. Sync (not Copy) so removed/renamed functions are pruned too.
// pack.mcmeta and README.md live outside function/ and are left untouched.
val syncExampleDatapack by tasks.registering(Sync::class) {
	description = "Copies the paintandseek datapack functions into example-datapack/."
	group = "build"
	from("src/main/resources/data/paintandseek/function")
	into("example-datapack/paintandseek-defaults/data/paintandseek/function")
}

// Keep the example datapack in sync whenever resources are processed (build/run).
tasks.processResources {
	finalizedBy(syncExampleDatapack)
}

// Package the example datapack into a drop-in zip alongside the jar in build/libs.
// Zips the *contents* of paintandseek-defaults/ so pack.mcmeta and data/ sit at the
// zip root, which is the structure Minecraft expects in a world's datapacks/ folder.
val datapackZip by tasks.registering(Zip::class) {
	description = "Packages the example datapack into a drop-in zip alongside the jar."
	group = "build"
	dependsOn(syncExampleDatapack) // ensure the functions are current first
	from("example-datapack/paintandseek-defaults")
	archiveFileName = "paintandseek-datapack-${project.version}.zip"
	destinationDirectory = layout.buildDirectory.dir("libs")
}

tasks.named("build") {
	dependsOn(datapackZip)
}

tasks.withType<JavaCompile>().configureEach {
	options.release = 25
}

kotlin {
	compilerOptions {
		jvmTarget = JvmTarget.JVM_25
	}
	// Use the same auto-provisioned JDK 25 toolchain as Java.
	jvmToolchain(25)
}

java {
	// Auto-provision JDK 25 via the Foojay resolver (configured in settings.gradle.kts)
	// so the build and run tasks use it without a system-wide install.
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}

	// Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
	// if it is present.
	// If you remove this line, sources will not be generated.
	withSourcesJar()

	sourceCompatibility = JavaVersion.VERSION_25
	targetCompatibility = JavaVersion.VERSION_25
}

tasks.jar {
	val projectName = project.name
	inputs.property("projectName", projectName)

	from("LICENSE") {
		rename { "${it}_$projectName" }
	}
}

// configure the maven publication
publishing {
	publications {
		register<MavenPublication>("mavenJava") {
			from(components["java"])
		}
	}

	// See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
	repositories {
		// Add repositories to publish to here.
		// Notice: This block does NOT have the same function as the block in the top level.
		// The repositories here will be used for publishing your artifact, not for
		// retrieving dependencies.
	}
}
