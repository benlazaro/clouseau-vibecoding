// Clouseau parser plugin template.
// Copy this directory, rename the classes, and implement your own LogParser.
//
// Build:  ./gradlew jar
// Install: drop the resulting JAR into ~/.clouseau/plugins/parser/ and restart Clouseau.

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
    compileOnly("com.tlaloc:clouseau-api:$clouseauVersion")
    compileOnly("org.pf4j:pf4j:$pf4jVersion")
    annotationProcessor("org.pf4j:pf4j:$pf4jVersion")

    testImplementation("com.tlaloc:clouseau-api:$clouseauVersion")
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
