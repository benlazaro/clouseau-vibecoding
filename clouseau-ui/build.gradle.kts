// clouseau-ui — Swing presentation layer (MVP)
// Rule: no business logic, no direct log parsing.

plugins {
    java
    application
}

application {
    mainClass.set("com.clouseau.ui.ClouseauApp")
}

dependencies {
    implementation(project(":clouseau-core"))
    implementation(project(":plugin-runtime"))

    implementation("com.google.guava:guava:${rootProject.extra["guavaVersion"]}")
    runtimeOnly("org.slf4j:slf4j-simple:${rootProject.extra["slf4jVersion"]}")
    implementation("com.formdev:flatlaf:${rootProject.extra["flatlafVersion"]}")
    implementation("com.formdev:flatlaf-intellij-themes:${rootProject.extra["flatlafVersion"]}")
    implementation("com.miglayout:miglayout-swing:${rootProject.extra["migLayoutVersion"]}")
    implementation("com.fifesoft:rsyntaxtextarea:${rootProject.extra["rstaVersion"]}")
}

// Copy built-in plugin JAR into the plugins/ folder alongside the app
tasks.named<ProcessResources>("processResources") {
    from(project(":plugins:builtin-parsers").tasks.named("jar"))
    into(layout.buildDirectory.dir("plugins"))
}
