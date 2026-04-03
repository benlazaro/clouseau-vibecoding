# Packaging assets

Place platform-specific icons here before running `jpackage`. The build will
include them automatically if found; if a file is missing the installer is
still produced, just without a custom icon.

```
packaging/
├── windows/
│   └── clouseau.ico    256×256 (multi-size ICO recommended)
├── macos/
│   └── clouseau.icns   1024×1024 (use iconutil or an online converter from the SVG)
└── linux/
    └── clouseau.png    128×128 PNG
```

The source SVG is at:
`clouseau-ui/src/main/resources/com/tlaloc/clouseau/ui/icons/clouseau.svg`

## Building the installer

Requires JDK 17+ and, on Windows, [WiX Toolset 3.x](https://wixtoolset.org/) on the PATH for MSI output.

```bash
# Native installer for the current platform
./gradlew :clouseau-ui:jpackage

# Output location
clouseau-ui/build/jpackage/
```

The installer bundles a trimmed JRE — no separate Java installation needed.

## Heap size

The default heap is `-Xms64m -Xmx2g`, baked into the native launcher via
`applicationDefaultJvmArgs` in `clouseau-ui/build.gradle.kts`. Adjust there
before packaging if your users typically work with very large files.
