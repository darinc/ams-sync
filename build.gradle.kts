plugins {
    kotlin("jvm") version "2.4.21"
    id("com.gradleup.shadow") version "9.6.1"
    id("dev.detekt") version "2.0.0-alpha.6"
}

group = "io.github.darinc"
version = "0.30.0"

repositories {
    mavenCentral()
    mavenLocal() // For locally-built MCMMO
    maven("https://repo.papermc.io/repository/maven-public/")
}

val paperVersion = providers.gradleProperty("paperVersion").get()
val mcmmoVersion = providers.gradleProperty("mcmmoVersion").get()

dependencies {
    // Paper 26.x API (requires Java 25)
    compileOnly("io.papermc.paper:paper-api:$paperVersion")

    // MCMMO - locally built and installed via: mvn install -DskipTests
    // Exclude transitive dependencies since we only need the API at compile time
    compileOnly("com.gmail.nossr50.mcMMO:mcMMO:$mcmmoVersion") {
        exclude(group = "com.sk89q.worldedit")
        exclude(group = "com.sk89q.worldguard")
        exclude(group = "com.sk89q")
    }

    // Discord
    implementation("net.dv8tion:JDA:6.7.0") {
        // No voice features: drop audio codec natives and voice encryption (per JDA docs)
        exclude(module = "opus-java")
        exclude(module = "tink")
    }
    // JDA 6.7.0 pins Jackson 2.22.2 (GHSA-7hhh-6rmp-j9qf, GHSA-p6pp-m3f8-5c89, GHSA-cxp5-3px4-pw24, GHSA-wv8q-qhhj-9h54)
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.23.0"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation(kotlin("stdlib"))

    // Logging - SLF4J Simple implementation for JDA
    implementation("org.slf4j:slf4j-api:2.0.20")
    implementation("org.slf4j:slf4j-simple:2.0.20")

    // Testing
    testImplementation("io.kotest:kotest-runner-junit5:6.2.7")
    testImplementation("io.kotest:kotest-assertions-core:6.2.7")
    testImplementation("io.kotest:kotest-property:6.2.7")
    testImplementation("io.mockk:mockk:1.14.12")
    testImplementation("io.papermc.paper:paper-api:$paperVersion")
    testImplementation("org.xerial:sqlite-jdbc:3.54.1.0")
    // MCMMO for tests (milestone card generation, etc.)
    testImplementation("com.gmail.nossr50.mcMMO:mcMMO:$mcmmoVersion") {
        exclude(group = "com.sk89q.worldedit")
        exclude(group = "com.sk89q.worldguard")
        exclude(group = "com.sk89q")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

kotlin {
    jvmToolchain(25)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks {
    // The shaded JAR is the deliverable; keep the plain JAR from overwriting it (same file name otherwise)
    jar {
        archiveClassifier.set("plain")
    }

    shadowJar {
        archiveClassifier.set("")
        // Relocate to avoid conflicts with other plugins
        relocate("net.dv8tion", "io.github.darinc.amssync.libs.jda")
        relocate("kotlin", "io.github.darinc.amssync.libs.kotlin")
        relocate("kotlinx", "io.github.darinc.amssync.libs.kotlinx")
        // Note: Don't relocate SLF4J - JDA needs to find it in original package

        // Exclude unnecessary files
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
    }

    build {
        dependsOn(shadowJar)
    }

    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}

// Static code analysis configuration
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("$projectDir/detekt-config.yml")
    ignoreFailures = true  // Report issues but don't fail build
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    reports {
        html.required.set(true)
        checkstyle.required.set(false)
    }
}
