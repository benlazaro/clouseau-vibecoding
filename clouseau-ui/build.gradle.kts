// clouseau-ui — Swing presentation layer (MVP)
// Rule: no business logic, no direct log parsing.

plugins {
    java
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

application {
    mainClass.set("com.tlaloc.clouseau.ui.ClouseauApp")
}

tasks.shadowJar {
    archiveBaseName.set("clouseau")
    archiveClassifier.set("")
    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

dependencies {
    implementation(project(":clouseau-core"))
    implementation(project(":clouseau-plugin"))

    implementation("com.google.guava:guava:${rootProject.extra["guavaVersion"]}")
    implementation("com.google.code.gson:gson:2.11.0")
    runtimeOnly("org.slf4j:slf4j-simple:${rootProject.extra["slf4jVersion"]}")
    implementation("com.formdev:flatlaf:${rootProject.extra["flatlafVersion"]}")
    implementation("com.formdev:flatlaf-intellij-themes:${rootProject.extra["flatlafVersion"]}")
    implementation("com.miglayout:miglayout-swing:${rootProject.extra["migLayoutVersion"]}")
    implementation("com.fifesoft:rsyntaxtextarea:${rootProject.extra["rstaVersion"]}")
    implementation("org.apache.xmlgraphics:batik-transcoder:1.17")
    implementation("org.apache.xmlgraphics:batik-codec:1.17")
    // PF4J is on the runtime classpath via clouseau-plugin; needed here only for compilation
    // because LogParser extends ExtensionPoint
    compileOnly("org.pf4j:pf4j:${rootProject.extra["pf4jVersion"]}")
}

