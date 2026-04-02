# Clouseau Log Viewer

A modern, pluggable log viewer for software engineers — built for large files, fast filtering, and a clean dark UI.

![Java 17](https://img.shields.io/badge/Java-17-blue) ![Gradle 8.8](https://img.shields.io/badge/Gradle-8.8-02303A) ![License MIT](https://img.shields.io/badge/License-MIT-green)

---

## Features

- **Large file support** — handles hundreds of thousands of log lines without breaking a sweat
- **Multi-format parsing** — Log4j pattern, Spring Boot, JSON; auto-detected on open
- **Compressed files** — open `.gz` and `.zip` files directly
- **Row highlighting** — color-code rows for visual triage; navigate between highlights by color
- **Filtering** — filter by level, logger, time range, and free-text message search (all columns)
- **Follow mode** — tail a live file like `tail -f`; survives log rotation without losing existing rows
- **Detail panel** — inspect the full entry with formatted, syntax-highlighted message; copy the message to clipboard
- **Formatters & colorizers** — built-in JSON pretty-printer and Monokai-inspired JSON colorizer; toggle or override per-entry from the detail toolbar
- **Find bar** — `Ctrl+F` searches all columns across visible rows with `↑`/`↓` navigation
- **Plugin system** — drop in a JAR to add new parsers, formatters, colorizers, or sources at runtime

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

**Highlight rows** — right-click one or more rows and choose a colour. Use the highlight nav bar to jump between highlighted rows, filtered by colour.

**Filter** — use the level toggles, logger picker, time range, and message search box. Filters combine (AND). Click **Clear All Filters** to reset all.
- **Level solo** — Alt+click a level button to show only that level; Alt+click it again to restore all.
- **Logger picker** — hierarchical tree with checkboxes; right-click the **Loggers** button to open. Click **Close** to dismiss without accidentally hitting **None**.

**Find** — press `Ctrl+F` to open the find bar. Searches all columns (message, logger, thread, level, custom fields) across visible rows. `Enter` / `Shift+Enter` or `▲` / `▼` to navigate matches. `Escape` to close.

**Detail panel** — select any row to see all fields expanded. The message is automatically formatted (JSON is pretty-printed) and colorized (Monokai palette).
- Toggle individual formatters and colorizers from the toolbar above the detail pane.
- Click **Copy Message** to copy the formatted message text to the clipboard (the message text flashes to confirm).

**Follow** — enabled by default. Toggleable per-tab in the filter bar. New lines are appended as they arrive; opening a file always starts at the top. Log rotation is handled transparently — existing rows are kept and the new file is tailed from the beginning.

**Tab context menu** — right-click any tab to **Reload** (re-read the file from scratch), **Clear Table** (remove all rows with confirmation), or **Close** the tab.

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

## Custom Parsers

You can add your own log format without writing a plugin. Create `~/.clouseau/parsers.json` as a JSON array of parser definitions:

```json
[
  {
    "name": "My App",
    "pattern": "(?<timestamp>\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}) \\[(?<thread>[^\\]]+)\\] (?<level>\\w+) (?<logger>\\S+) - (?<message>.*)",
    "timestampFormat": "yyyy-MM-dd HH:mm:ss"
  }
]
```

| Field | Required | Description |
|---|---|---|
| `name` | Yes | Display name shown in the file chooser |
| `pattern` | Yes | Java regex with named capture groups |
| `timestampFormat` | Yes | `DateTimeFormatter` pattern for the `timestamp` group (e.g. `yyyy-MM-dd HH:mm:ss.SSS`). See note below. |

**Named capture groups** map to log entry fields:

| Group | Maps to |
|---|---|
| `timestamp` | Parsed timestamp (requires `timestampFormat`) |
| `level` | Log level (`TRACE` `DEBUG` `INFO` `WARN` `ERROR` `FATAL`) |
| `logger` | Logger name |
| `thread` | Thread name |
| `message` | Log message |
| anything else | Stored as an extra field, visible in the detail panel |

All groups are optional — only define what your format includes. Parsers are loaded on startup; restart the app after editing the file. Invalid entries are skipped with a warning in the log.

> **`timestampFormat`** accepts any [`DateTimeFormatter`](https://docs.oracle.com/en/java/docs/api/java.base/java/time/format/DateTimeFormatter.html) pattern string (e.g. `yyyy-MM-dd HH:mm:ss.SSS`). One special value is also supported: `ISO_OFFSET_DATE_TIME`, which matches timestamps that include a timezone offset or `Z` suffix — for example `2024-01-15 10:30:45.123+01:00` or `2024-01-15 10:30:45.789Z`. Use this for Spring Boot logs. Note that `,` is automatically normalised to `.` before parsing, so `yyyy-MM-dd HH:mm:ss.SSS` matches both `10:30:45.123` and `10:30:45,123`.

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
  "follow.by.default": true,
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
