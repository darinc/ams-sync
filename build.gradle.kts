plugins {
    kotlin("jvm") version "1.9.21"
    id("com.gradleup.shadow") version "9.3.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.3"
}

group = "io.github.darinc"
version = "0.3.1"

repositories {
    mavenCentral()
    mavenLocal() // For locally-built MCMMO
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Paper 1.21.4 API (compatible with 1.21.x dev builds)
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")

    // MCMMO - locally built and installed via: mvn install -DskipTests
    // Exclude transitive dependencies since we only need the API at compile time
    compileOnly("com.gmail.nossr50.mcMMO:mcMMO:2.2.044-SNAPSHOT") {
        exclude(group = "com.sk89q.worldedit")
        exclude(group = "com.sk89q.worldguard")
        exclude(group = "com.sk89q")
    }

    // Discord
    implementation("net.dv8tion:JDA:5.0.0-beta.18")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation(kotlin("stdlib"))

    // Logging - SLF4J Simple implementation for JDA
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("org.slf4j:slf4j-simple:2.0.9")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        // Relocate to avoid conflicts with other plugins
        relocate("net.dv8tion", "io.github.darinc.amsdiscord.libs.jda")
        relocate("kotlin", "io.github.darinc.amsdiscord.libs.kotlin")
        relocate("kotlinx", "io.github.darinc.amsdiscord.libs.kotlinx")
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
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "20"  // Detekt doesn't support JVM 21 yet
    reports {
        html.required.set(true)
        xml.required.set(false)
        txt.required.set(false)
    }
}
