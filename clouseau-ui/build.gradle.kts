// clouseau-ui — Swing presentation layer (MVP)
// Rule: no business logic, no direct log parsing.

plugins {
    java
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.beryx.runtime") version "1.13.1"
}

application {
    mainClass.set("com.tlaloc.clouseau.ui.ClouseauApp")
    // Applied to installDist launchers and the jpackage image.
    // Users can override at runtime with -J-Xmx in the launcher or JAVA_OPTS.
    applicationDefaultJvmArgs = listOf("-Xms64m", "-Xmx2g")
}

tasks.processResources {
    filesMatching("version.properties") {
        expand("appVersion" to project.version)
    }
}

tasks.shadowJar {
    archiveBaseName.set("clouseau")
    archiveClassifier.set("")
    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

// jpackage requires a purely numeric version (e.g. 1.0.0) — strip -SNAPSHOT.
val pkgVersion: String = (project.version as String).removeSuffix("-SNAPSHOT")

runtime {
    // jlink options: produce a lean JRE image.
    options.set(listOf(
        "--strip-debug",
        "--compress", "2",
        "--no-header-files",
        "--no-man-pages"
    ))

    // Modules required by the app and its dependencies.
    // java.desktop  — Swing / AWT
    // java.logging  — java.util.logging (used internally)
    // java.management — JMX (Guava, log4j2)
    // java.naming   — JNDI (log4j2 lookups)
    // java.prefs    — Preferences API
    // java.sql      — JDBC stubs pulled in transitively
    // java.xml      — XML (Batik SVG, log4j2 config)
    // jdk.unsupported — sun.misc.Unsafe (Guava, Gson)
    // jdk.crypto.ec — TLS EC ciphers (HTTPS downloads, update checks)
    // jdk.zipfs     — ZipFileSystem (reading .zip log files)
    modules.set(listOf(
        "java.desktop",
        "java.logging",
        "java.management",
        "java.naming",
        "java.prefs",
        "java.sql",
        "java.xml",
        "jdk.unsupported",
        "jdk.crypto.ec",
        "jdk.zipfs"
    ))

    jpackage {
        imageName     = "Clouseau"
        installerName = "Clouseau"
        appVersion    = pkgVersion

        // JVM flags baked into the native launcher.
        jvmArgs = listOf("-Xms64m", "-Xmx2g")

        val os           = org.gradle.internal.os.OperatingSystem.current()
        val packagingDir = project.file("packaging")

        when {
            os.isWindows -> {
                installerType = "msi"
                val ico = packagingDir.resolve("windows/clouseau.ico")
                if (ico.exists()) imageOptions = listOf("--icon", ico.absolutePath)
                installerOptions = listOf(
                    "--vendor", "Tlaloc",
                    "--win-dir-chooser",
                    "--win-menu",
                    "--win-shortcut",
                    "--win-menu-group", "Clouseau",
                    // Fixed UUID — Windows uses this to match installed versions for upgrade/uninstall.
                    // Do NOT change this after the first public release.
                    "--win-upgrade-uuid", "3f8a7c2e-1d94-4b60-9e5f-0a2b6c8d3e7f"
                )
            }
            os.isMacOsX -> {
                installerType = "dmg"
                val icns = packagingDir.resolve("macos/clouseau.icns")
                if (icns.exists()) imageOptions = listOf("--icon", icns.absolutePath)
                installerOptions = listOf(
                    "--vendor", "Tlaloc",
                    "--mac-package-name", "Clouseau"
                )
            }
            else -> {
                installerType = "deb"
                val png = packagingDir.resolve("linux/clouseau.png")
                if (png.exists()) imageOptions = listOf("--icon", png.absolutePath)
                installerOptions = listOf(
                    "--vendor", "Tlaloc",
                    "--linux-shortcut",
                    "--linux-menu-group", "Development;Utility;",
                    "--linux-app-category", "utils"
                )
            }
        }
    }
}

dependencies {
    implementation(project(":clouseau-core"))
    implementation(project(":clouseau-plugin"))

    implementation("com.google.guava:guava:${rootProject.extra["guavaVersion"]}")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.apache.logging.log4j:log4j-core:2.23.0")
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.23.0")
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

