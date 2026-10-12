plugins {
    `java-library`
}

base {
    archivesName.set("texture-mixer-core")
}

// The core must run on every supported Minecraft version, so it targets the oldest
// Java release among them (1.20.1 runs on Java 17).
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

dependencies {
    // Both ship with every Minecraft version this mod targets, so they are not bundled.
    compileOnlyApi("com.google.code.gson:gson:${property("gson_version")}")
    compileOnlyApi("org.slf4j:slf4j-api:${property("slf4j_version")}")

    testImplementation("com.google.code.gson:gson:${property("gson_version")}")
    testImplementation("org.slf4j:slf4j-api:${property("slf4j_version")}")
    testImplementation(platform("org.junit:junit-bom:${property("junit_version")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
