// clouseau-api — the plugin contract
// Third-party plugins only ever depend on THIS module.
// Rule: no Swing, no core internals, no heavy runtime deps.

plugins {
    java
}

dependencies {
    // PF4J annotations (@ExtensionPoint, @Extension) — compile-only, no runtime cost
    compileOnly("org.pf4j:pf4j:${rootProject.extra["pf4jVersion"]}")
}
