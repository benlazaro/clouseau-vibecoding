// clouseau-core — engine, data model, event bus, log index
// Rule: NO Swing/UI imports allowed here.

plugins {
    `java-library`
}

dependencies {
    api(project(":clouseau-api"))
    implementation("com.google.guava:guava:${rootProject.extra["guavaVersion"]}")
    compileOnly("org.pf4j:pf4j:${rootProject.extra["pf4jVersion"]}")
    testCompileOnly("org.pf4j:pf4j:${rootProject.extra["pf4jVersion"]}")
    testRuntimeOnly("org.pf4j:pf4j:${rootProject.extra["pf4jVersion"]}")
}
