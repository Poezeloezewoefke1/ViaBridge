// Loom is put on the build classpath here, once, so every fabric/<version> project shares
// a single Loom instance (Loom misbehaves when loaded by several classloaders).
// With -PcoreOnly nothing Fabric-related is resolved, which lets the core module build
// and test on machines that cannot reach maven.fabricmc.net.
buildscript {
    if (!project.hasProperty("coreOnly")) {
        repositories {
            maven("https://maven.fabricmc.net/") { name = "Fabric" }
            gradlePluginPortal()
            mavenCentral()
        }
        dependencies {
            classpath("net.fabricmc:fabric-loom:${project.property("loom_version")}")
        }
    }
}

allprojects {
    group = rootProject.property("maven_group") as String
    version = rootProject.property("mod_version") as String

    repositories {
        mavenCentral()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
}
