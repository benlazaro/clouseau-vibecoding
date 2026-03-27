// clouseau-plugin — discovers, loads, and manages plugin lifecycle via PF4J

plugins {
    java
}

dependencies {
    implementation(project(":clouseau-core"))
    implementation("org.pf4j:pf4j:${rootProject.extra["pf4jVersion"]}")
}
