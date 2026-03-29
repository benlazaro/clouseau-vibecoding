# Clouseau Log Viewer

A modern, pluggable log viewer for software engineers — built for large files, fast filtering, and a clean dark UI.

![Java 17](https://img.shields.io/badge/Java-17-blue) ![Gradle 8.8](https://img.shields.io/badge/Gradle-8.8-02303A) ![License MIT](https://img.shields.io/badge/License-MIT-green)

---

## Features

- **Large file support** — handles hundreds of thousands of log lines without breaking a sweat
- **Multi-format parsing** — Log4j pattern, Spring Boot, JSON; auto-detected on open
- **Compressed files** — open `.gz` and `.zip` files directly
- **Row highlighting** — color-code rows for visual triage; navigate between highlights by color
- **Filtering** — filter by level, logger, time range, and free-text message search
- **Follow mode** — tail a live file like `tail -f`
- **Detail panel** — inspect the full entry with syntax-highlighted field breakdown
- **Plugin system** — drop in a JAR to add new parsers, sources, or filters at runtime

---

## Getting Started

### Requirements

- Java 17+

### Run from source

```bash
git clone https://github.com/yourorg/clouseau.git
cd clouseau
./gradlew :clouseau-ui:run
```

### Build a distribution

```bash
./gradlew :clouseau-ui:installDist
./clouseau-ui/build/install/clouseau-ui/bin/clouseau-ui
```

---

## Usage

**Open a file** — `File → Open` or `Ctrl+O`. Clouseau auto-detects the format from the first lines of the file; you can also pick a parser manually from the file chooser.

### Built-in parsers

#### Log4j Pattern

Pattern: `%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %logger - %msg`

```
{date} [{thread}] {level} {logger} - {message}
```

Supports space or `T` as the date/time separator, and `.` or `,` as the millisecond separator.

```
2024-01-15 10:30:45.123 [main] INFO  com.example.App - Application started
2024-01-15 10:30:45,456 [http-nio-8080-exec-1] ERROR com.example.OrderService - Order not found
2024-01-15T10:30:46.001 [scheduler-1] WARN  com.example.Cache - Cache miss rate high
```

#### Spring Boot

Default Logback console format. Two variants are supported:

**Pre-3.4** — `%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %5p ${PID} --- [%t] %-40.40logger{39} : %m`

```
{date+tz}  {level} {pid} --- [{thread}] {logger} : {message}
```

```
2024-01-15T10:30:45.123+01:00  INFO 12345 --- [           main] com.example.App           : Started App in 2.3s
2024-01-15T10:30:46.007+01:00  WARN 12345 --- [pool-1-thread-2] com.example.Cache         : Eviction rate above threshold
2024-01-15T10:30:47.512+01:00  ERROR 12345 --- [http-nio-8080-exec-3] com.example.OrderService  : Order not found
```

**3.4+** — adds an `[appName]` group between `---` and the thread:

```
{date+tz}  {level} {pid} --- [{appName}] [{thread}] {logger} : {message}
```

```
2025-06-10T23:43:06.269-05:00  INFO 31280 --- [demo] [  restartedMain] com.example.App           : Started App in 1.8s
2025-06-10T23:43:07.001-05:00  ERROR 31280 --- [demo] [http-nio-8080-exec-1] com.example.OrderService  : Order #4291 not found
```

**Highlight rows** — right-click one or more rows and choose a colour. Use the highlight nav bar at the top to jump between highlighted rows, filtered by colour.

**Filter** — use the level toggles, logger picker, time range, and search box. Filters combine (AND). Click **Clear** to reset all.

**Follow** — toggle **Follow** in the filter bar to auto-scroll as new lines arrive. Only active when a file is being tailed.

**Settings** — `File → Settings` (`Ctrl+,`). Stored at `~/.clouseau/settings.json`.

---

## Project Structure

```
clouseau/
├── clouseau-api/       # Plugin contract — interfaces only (LogParser, LogFilter, LogSource)
├── clouseau-core/      # Engine — LogEntry, LogIndex, ClouseauEventBus, built-in parsers
├── clouseau-plugin/    # PF4J plugin loader and lifecycle
└── clouseau-ui/        # Swing UI — MainFrame, LogPanel, FilterBar, SettingsDialog
```

**Dependency rules:**

```
clouseau-api  ←  clouseau-core  ←  clouseau-plugin  ←  clouseau-ui
                                                         (plugins depend only on clouseau-api)
```

---

## Writing a Plugin

Implement `LogParser` (or `LogFilter` / `LogSource`) from `clouseau-api`, annotate with `@Extension`, package as a PF4J plugin JAR, and drop it in the plugins directory.

```java
@Extension
public class MyParser implements LogParser {
    @Override public String getName() { return "My Format"; }

    @Override public boolean canParse(String line) { /* sniff the line */ }

    @Override public Optional<LogEntry> parse(String line) { /* parse it */ }
}
```

---

## Tech Stack

| Concern | Library |
|---|---|
| Look & feel | FlatLaf 3.4.1 + One Dark theme |
| Layout | MigLayout 11.3 |
| Log text rendering | RSyntaxTextArea 3.4.0 |
| Event bus | Guava 33 AsyncEventBus |
| Plugin system | PF4J 3.12.0 |
| Build | Gradle 8.8 (Kotlin DSL), Java 17 |
| Tests | JUnit 5, AssertJ, Mockito |

---

## Settings

User settings are stored at `~/.clouseau/settings.json`:

```json
{
  "tab.close.confirm": true,
  "follow.by.default": false,
  "detail.font.size": 12,
  "recent.max": 5,
  "recent.files": [
    "/home/user/logs/app.log"
  ]
}
```

---

## License

MIT
