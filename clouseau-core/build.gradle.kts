// clouseau-core — engine, data model, event bus, log index
// Rule: NO Swing/UI imports allowed here.

plugins {
    `java-library`
}

dependencies {
    api(project(":clouseau-api"))
    implementation("com.google.guava:guava:${rootProject.extra["guavaVersion"]}")
}
