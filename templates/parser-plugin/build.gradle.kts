// Clouseau parser plugin template.
// Copy this directory, rename the classes, and implement your own LogParser.
//
// Before building, generate clouseau-api.jar from the main project:
//   ./gradlew :clouseau-api:jar
//
// Then build this plugin:
//   ./gradlew jar
//
// Drop the resulting JAR into ~/.clouseau/plugins/ and restart Clouseau.

plugins {
    java
}

group   = "com.example"          // ← change to your group
version = "1.0.0"

val pf4jVersion     = "3.12.0"
val clouseauVersion = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // clouseau-api must be built from the main project first (see above)
    compileOnly("org.tlaloc:clouseau-api:$clouseauVersion"))
    compileOnly("org.pf4j:pf4j:$pf4jVersion")
    annotationProcessor("org.pf4j:pf4j:$pf4jVersion")

    testImplementation("org.tlaloc:clouseau-api:$clouseauVersion"))
    testCompileOnly("org.pf4j:pf4j:$pf4jVersion")
    testRuntimeOnly("org.pf4j:pf4j:$pf4jVersion")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<Test> { useJUnitPlatform() }

tasks.jar {
    manifest {
        attributes(
            "Plugin-Id"      to "my-parser-plugin",                    // ← change this
            "Plugin-Version" to project.version,
            "Plugin-Class"   to "com.example.MyParserPlugin"           // ← change this
        )
    }
}
