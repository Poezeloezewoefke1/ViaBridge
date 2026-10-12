pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "texture-mixer"

// Version-independent logic: pack scanning, texture indexing, selections, profiles,
// history and pack generation. Plain Java, no Minecraft classes, unit-tested.
include("core")

// One Gradle project per supported Minecraft version, e.g. fabric/1.21.4.
// Each compiles fabric/common (shared Minecraft-facing code) together with its own
// version-specific compat layer.
//
//   ./gradlew build                 -> core + every version
//   ./gradlew build -Pmc=1.21.4     -> core + only that version
//   ./gradlew :core:test -PcoreOnly -> core only (no Fabric/Minecraft downloads at all)
val coreOnly = providers.gradleProperty("coreOnly").isPresent
val onlyVersion = providers.gradleProperty("mc").orNull

if (!coreOnly) {
    file("fabric").listFiles()
        ?.filter { it.isDirectory && file("${it.path}/gradle.properties").exists() }
        ?.filter { onlyVersion == null || it.name == onlyVersion }
        ?.sortedBy { it.name }
        ?.forEach { dir ->
            val path = ":fabric-${dir.name}"
            include(path)
            project(path).projectDir = dir
        }
}
