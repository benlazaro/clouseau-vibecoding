// plugins/parser-template — reference implementation showing how to write a LogParser plugin

plugins {
    java
}

dependencies {
    compileOnly(project(":clouseau-api"))
    compileOnly("org.pf4j:pf4j:${rootProject.extra["pf4jVersion"]}")
    annotationProcessor("org.pf4j:pf4j:${rootProject.extra["pf4jVersion"]}")

    testImplementation(project(":clouseau-api"))
    testCompileOnly("org.pf4j:pf4j:${rootProject.extra["pf4jVersion"]}")
    testRuntimeOnly("org.pf4j:pf4j:${rootProject.extra["pf4jVersion"]}")
}

tasks.jar {
    manifest {
        attributes(
            "Plugin-Id"      to "parser-template",
            "Plugin-Version" to project.version,
            "Plugin-Class"   to "com.clouseau.plugins.parsers.ParserTemplatePlugin"
        )
    }
}
