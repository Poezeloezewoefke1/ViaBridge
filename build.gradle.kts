plugins {
    id("fabric-loom") version "1.9-SNAPSHOT"
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

repositories {
    mavenCentral()
    maven("https://repo.viaversion.com/") { name = "ViaVersion" }
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings(loom.officialMojangMappings())

    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")

    // Via libraries.
    //
    // transitive = false on purpose: ViaVersion pulls in Guava/fastutil/gson, all of
    // which Minecraft already ships. Letting them through causes duplicate-class and
    // classloader conflicts under runClient.
    //
    // include(...) nests the jars inside our own jar so the built mod is self-contained
    // and the user does not have to install anything else.
    val viaVersion = project.property("viaversion_version")
    val viaBackwards = project.property("viabackwards_version")

    implementation("com.viaversion:viaversion:$viaVersion") { isTransitive = false }
    include("com.viaversion:viaversion:$viaVersion")

    implementation("com.viaversion:viabackwards:$viaBackwards") { isTransitive = false }
    include("com.viaversion:viabackwards:$viaBackwards")
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", project.property("minecraft_version"))
    inputs.property("loader_version", project.property("loader_version"))

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version" to project.property("minecraft_version"),
            "loader_version" to project.property("loader_version"),
        )
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
}

loom {
    runs {
        named("client") {
            client()
            ideConfigGenerated(true)
            runDir("run")
        }
    }
}
