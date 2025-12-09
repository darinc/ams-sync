# Building AMSSync

**Complexity**: Beginner
**Key File**: [`build.gradle.kts`](../../build.gradle.kts)

## Prerequisites

- **Java 21** or higher
- **Gradle** (wrapper included)
- **MCMMO** installed to local Maven repository

## Installing MCMMO Dependency

MCMMO is not published to Maven Central. You must build and install it locally:

```bash
# Clone MCMMO
git clone https://github.com/mcMMO-Dev/mcMMO.git
cd mcMMO

# Install to local Maven repository
mvn install -DskipTests
```

This creates `~/.m2/repository/com/gmail/nossr50/mcMMO/mcMMO/...`

## Build Commands

### Build Plugin JAR

```bash
./gradlew shadowJar
```

Output: `build/libs/ams-sync-0.11.3.jar`

The Shadow plugin creates a "fat JAR" with all dependencies included.

### Clean Build

```bash
./gradlew clean build
```

### Run Tests

```bash
./gradlew test
```

Test report: `build/reports/tests/test/index.html`

### Run Static Analysis

```bash
./gradlew detekt
```

HTML report: `build/reports/detekt/detekt.html`

### Run All Checks

```bash
./gradlew check
```

Runs both tests and detekt.

### Compile Only (Fast Check)

```bash
./gradlew compileKotlin
```

Quick syntax/type check without full build.

## Understanding the Build

### Plugins

```kotlin
plugins {
    kotlin("jvm") version "1.9.21"              // Kotlin compiler
    id("com.gradleup.shadow") version "9.3.0"  // Fat JAR creation
    id("io.gitlab.arturbosch.detekt") version "1.23.3"  // Static analysis
}
```

### Dependencies

```kotlin
dependencies {
    // Compile-only: Provided by server at runtime
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("com.gmail.nossr50.mcMMO:mcMMO:2.2.044-SNAPSHOT")

    // Implementation: Bundled in JAR
    implementation("net.dv8tion:JDA:5.0.0-beta.18")
    implementation("club.minnced:discord-webhooks:0.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("org.slf4j:slf4j-simple:2.0.9")

    // Testing
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
    testImplementation("io.mockk:mockk:1.13.8")
}
```

### Java/Kotlin Version

```kotlin
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
}
```

## Shadow JAR Configuration

### Dependency Relocation

Prevents conflicts with other plugins:

```kotlin
tasks {
    shadowJar {
        // Relocate packages to avoid conflicts
        relocate("net.dv8tion", "io.github.darinc.amssync.libs.jda")
        relocate("club.minnced", "io.github.darinc.amssync.libs.webhook")
        relocate("kotlin", "io.github.darinc.amssync.libs.kotlin")
        relocate("kotlinx", "io.github.darinc.amssync.libs.kotlinx")

        // Note: Don't relocate SLF4J - JDA needs original package
    }
}
```

**Why relocate?** Other plugins might use different versions of the same libraries. Relocation gives each plugin its own copy.

### Excluded Files

```kotlin
shadowJar {
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
}
```

Removes signature files that can cause issues with shaded JARs.

## Resource Processing

Version injection into `plugin.yml`:

```kotlin
tasks {
    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
```

In `plugin.yml`:
```yaml
version: ${version}  # Replaced with actual version at build time
```

## Detekt Configuration

```kotlin
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("$projectDir/detekt-config.yml")
}
```

Custom rules in `detekt-config.yml` override defaults.

## Common Issues

### "Could not resolve com.gmail.nossr50.mcMMO:mcMMO"

MCMMO not installed locally. Run:
```bash
cd /path/to/mcMMO
mvn install -DskipTests
```

### "Unsupported class file major version 65"

Java version mismatch. Ensure Java 21:
```bash
java -version
# Should show: openjdk version "21.x.x"
```

### Shadow JAR Too Large

Check for unnecessary transitive dependencies. Exclude them:
```kotlin
compileOnly("some:dependency") {
    exclude(group = "unwanted")
}
```

### Detekt Failures

View report for details:
```bash
./gradlew detekt
open build/reports/detekt/detekt.html
```

## IDE Setup

### IntelliJ IDEA

1. Open project folder
2. Import as Gradle project
3. Wait for indexing
4. Mark `src/main/kotlin` as Sources Root
5. Mark `src/test/kotlin` as Test Sources Root

### VS Code

Install extensions:
- Kotlin Language
- Gradle for Java

## Continuous Integration

Example GitHub Actions workflow:

```yaml
name: Build

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Build MCMMO dependency
        run: |
          git clone https://github.com/mcMMO-Dev/mcMMO.git
          cd mcMMO
          mvn install -DskipTests

      - name: Build with Gradle
        run: ./gradlew build

      - name: Upload artifact
        uses: actions/upload-artifact@v4
        with:
          name: plugin-jar
          path: build/libs/*.jar
```

## Installation

After building:

```bash
# Copy to server
cp build/libs/ams-sync-*.jar /path/to/server/plugins/

# Start server to generate config
# Stop server, edit config
# Restart server
```

## Related Documentation

- [Getting Started](../getting-started.md) - Full setup guide
- [Configuration](../configuration.md) - Config reference
- [Testing](testing.md) - Test patterns
