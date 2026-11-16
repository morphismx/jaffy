plugins {
    `java-library`
}

group = "net.morphismmc"
version = rootProject.version

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    // The `serde` feature of the port: Gson adapters for all serde-decorated
    // jaffy types (see docs/porting/DECISIONS.md §jaffy-gson).
    api(project(":"))

    // Gson is shipped by the Minecraft runtime. 2.8.0 is the MC 1.12.2
    // baseline: only APIs present there may be used (no JsonWriter.jsonValue).
    compileOnly("com.google.code.gson:gson:2.8.0")
    testImplementation("com.google.code.gson:gson:2.8.0")

    // jaffy's own compileOnlyApi deps, needed when tests exercise style types.
    testImplementation("it.unimi.dsi:fastutil:7.1.0")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
