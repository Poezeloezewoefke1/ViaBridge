// Texture Mixer for Minecraft 1.21.4.
//
// Compiles the version-independent core, the shared Minecraft code in fabric/common and
// this folder's compat layer into one mod jar. Other fabric/<version> folders follow the
// same layout; only gradle.properties and the compat sources differ.
plugins {
    id("fabric-loom")
}

val minecraftVersion = property("minecraft_version") as String
val javaRelease = (property("java_release") as String).toInt()
val minecraftDependency = property("minecraft_dependency") as String

version = "${rootProject.property("mod_version")}+mc$minecraftVersion"

base {
    archivesName.set("${rootProject.property("archives_base_name")}-fabric")
}

repositories {
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
}

sourceSets {
    main {
        java.srcDir(rootProject.file("core/src/main/java"))
        java.srcDir(rootProject.file("fabric/common/src/main/java"))
        resources.srcDir(rootProject.file("fabric/common/src/main/resources"))
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:${property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
}

// Client game tests: `./gradlew :fabric-1.21.4:runClientGameTest` starts the real game,
// installs test packs, drives the Texture Mixer screens and checks the generated pack.
// Needs a display (CI uses xvfb).
fabricApi {
    configureTests {
        createSourceSet.set(true)
        modId.set("texturemixer-gametest")
        enableGameTests.set(false)
        enableClientGameTests.set(true)
        eula.set(true)
    }
}

loom {
    runs {
        named("client") {
            runDir("run")
        }
    }
}

tasks.processResources {
    val properties = mapOf(
        "version" to project.version.toString(),
        "minecraft_dependency" to minecraftDependency,
        "java_release" to javaRelease,
    )
    inputs.properties(properties)
    filesMatching("fabric.mod.json") {
        expand(properties)
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(javaRelease)
}

tasks.jar {
    from(rootProject.file("LICENSE"))
}
