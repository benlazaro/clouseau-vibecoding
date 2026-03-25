// plugins/builtin-parsers — ships with the app as a regular plugin JAR

plugins {
    java
}

dependencies {
    compileOnly(project(":clouseau-api"))
    compileOnly("org.pf4j:pf4j:${rootProject.extra["pf4jVersion"]}")
}

tasks.jar {
    manifest {
        attributes(
            "Plugin-Id"      to "builtin-parsers",
            "Plugin-Version" to project.version,
            "Plugin-Class"   to "com.clouseau.plugins.parsers.BuiltinParsersPlugin"
        )
    }
}
