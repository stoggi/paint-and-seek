pluginManagement {
	repositories {
		maven {
			name = "Fabric"
			url = uri("https://maven.fabricmc.net/")
		}
		mavenCentral()
		gradlePluginPortal()
	}

	plugins {
		id("net.fabricmc.fabric-loom") version providers.gradleProperty("loom_version")
	}
}

plugins {
	// Resolves and auto-provisions JDK toolchains (e.g. JDK 25) into Gradle's own
	// cache (~/.gradle/jdks) so no system-wide JDK install is required.
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// Should match your modid
rootProject.name = "paintandseek"
