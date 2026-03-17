# JavaFX Version – Clouseau Log Viewer Outline

High-level design for reimplementing the Log4j2 log viewer as a JavaFX desktop app. Use this as a spec or checklist when building.

---

## 1. Project setup

- **Build**: Maven or Gradle.
- **Java**: 17+ (LTS); JavaFX is bundled in 17+ via `javafx.controls`, `javafx.fxml` (optional).
- **Module path**: If using modules, add `requires javafx.controls;` and declare the module in `module-info.java`.

**Dependencies (Maven example):**

```xml
<!-- JavaFX (or use platform BOM) -->
<dependency>
  <groupId>org.openjfx</groupId>
  <artifactId>javafx-controls</artifactId>
  <version>21</version>
</dependency>
<dependency>
  <groupId>org.openjfx</groupId>
  <artifactId>javafx-fxml</artifactId>
  <version>21</version>
</dependency>
```

- **Packaging**: `jlink` + `jpackage` for a custom runtime image and native installer (no full JDK on user machine); or fat JAR + `java -jar` if you assume a system JRE.

---

## 2. Project structure (packages)

```
src/main/java/
  com.example.logviewer/
    LogViewerApp.java           // Application entry, Stage, primary Scene
    ui/
      MainController.java       // Or split: TabPane + Toolbar + List
      TabController.java        // One tab’s content: toolbar + table/list
    model/
      LogEntry.java             // level, timestamp, logger, message, raw, stackTrace
      LogTabModel.java          // ObservableList<LogEntry>, filePath, tailing flag
      AppState.java             // Format config, bookmarks, window prefs
    parsing/
      LogFormatConfig.java      // Plain (regex) vs JSON, pattern / keys
      LogParser.java            // Parse line → LogEntry; multiline/stack handling
    io/
      FileOpener.java           // Read file (optionally chunked for large files)
      FileWatcher.java          // WatchService or NIO watch for tail
      ClipboardPaste.java      // Paste from system clipboard
    storage/
      PreferencesStore.java    // Format config, bookmarks (Preferences or JSON file)
```

- **Resources**: CSS for theming and level colors; optional FXML for layout.

---

## 3. Application shell

- **Stage**: One main window.
- **Scene**: Root layout (e.g. `BorderPane`).
  - **Top**: MenuBar (File → Open, Paste, Export; View → Format, Bookmarks) and/or Toolbar (buttons + filters).
  - **Center**: `TabPane` — one tab per opened file or paste.
- **Per-tab content**: Toolbar (level filter, search, follow, time range) + virtualized list of log lines.

---

## 4. Log model and parsing

- **LogEntry**: Immutable or record: `level`, `timestamp`, `logger`, `message`, `raw`, `stackTrace` (optional), `lineNumber`.
- **LogParser**:
  - **Plain**: Configurable regex with groups (timestamp, level, thread, logger, message). Default pattern for common Log4j2 layout. Multiline: lines that look like stack (leading whitespace or `"at "`) append to previous entry’s `stackTrace`.
  - **JSON**: Parse one JSON object per line; configurable keys for level, timestamp, message, logger.
- **Format config**: Stored in Preferences (or JSON); UI to edit regex or JSON keys and reload current tab.

---

## 5. Data loading and tailing

- **Open file**:
  - For files under ~50MB: read fully, split lines, parse into `ObservableList<LogEntry>`.
  - For larger files (up to 1GB): read last N bytes (e.g. 10MB), parse only that tail; optional “Load older” to read previous chunk (reverse read or memory-mapped).
- **Paste**: Read from `Clipboard.getSystemClipboard().getString()`; parse and create a new tab with an in-memory list.
- **Tail**: For an open file tab, start a `WatchService` (or polling with last-modified + position) on the file; on change, read new bytes from last known position, parse new lines, append to `ObservableList<LogEntry>` on the JavaFX thread (`Platform.runLater`).

---

## 6. UI components

| Feature            | JavaFX approach |
|--------------------|-----------------|
| Tabs               | `TabPane` + `Tab`; each tab’s content = toolbar + list. |
| Toolbar            | `HBox` / `FlowPane` with buttons, `ComboBox` (level), `TextField` (search), `CheckBox` (regex, follow). |
| Log list           | `TableView<LogEntry>` with columns (time, level, logger, message) and optional “raw” column; or `ListView` with custom cell factory. Use **virtualized** controls (TableView/ListView are virtualized by default) and limit visible row height so 100k+ rows stay responsive. |
| Level colors       | TableRow or ListCell: set style class or inline style from `entry.getLevel()` (e.g. `.level-error` in CSS). |
| Search highlight   | In cell: format `message` with highlighted substring (e.g. `Text` with `TextFlow` and styled segments). |
| Stack traces       | Expand-on-click: extra row or accordion under the main row, or popup; show `entry.getStackTrace()`. |
| Time range filter  | Two `TextField`s (from/to); filter the observable list or a filtered wrapper (e.g. `FilteredList`). |

---

## 7. Filtering and search

- Keep full list in the model; expose a **FilteredList** (or custom `ObservableList` wrapper) for:
  - Level (equals),
  - Time range (timestamp in range),
  - Search (substring or regex on `raw` or `message`).
- Bind the TableView/ListView to this filtered list so only matching rows are shown.
- **Export**: Write the filtered list’s `raw` lines to a file (FileChooser for save path).

---

## 8. Follow / tail behavior

- **Follow checkbox**: When tail is active and “Follow” is on, after appending new entries scroll the table/list to the last item (e.g. `tableView.scrollTo(index)`).
- Run file-watch and append logic on a background thread; update UI only via `Platform.runLater`.

---

## 9. Format config and bookmarks

- **Format**:
  - Dialog or separate window: radio Plain/JSON; for Plain: text field for regex (+ optional hint); for JSON: text fields for level/timestamp/message/logger keys.
  - On save: persist via `Preferences` or JSON file; re-parse current tab (or all tabs) with new format.
- **Bookmarks**:
  - Model: list of `{ tabId, lineNumber, optionalLabel }`; persist in Preferences or JSON.
  - UI: “Bookmarks” window or panel listing saved lines; “Go to” selects tab and scrolls to that line (e.g. `tableView.getSelectionModel().select(lineIndex)` and `scrollTo(lineIndex)`).
  - In the log list: star/button per row to add/remove bookmark for that line.

---

## 10. Theming and styling

- One main CSS file (e.g. `styles.css`) attached to the scene.
- Define `.level-debug`, `.level-info`, `.level-warn`, `.level-error` (and optionally trace/fatal) for row or cell background/text color.
- Optional: dark theme variables (e.g. `-fx-base`) to match the current Electron look.

---

## 11. Build and run

- **Run**: `mvn javafx:run` or Gradle `run` with main class pointing to `LogViewerApp`.
- **Package**: 
  - `jlink` to produce a custom runtime image (only needed modules).
  - `jpackage` to create installer (MSI/exe, DMG, deb/rpm) so the app runs without installing a JDK.

---

## 12. Feature parity checklist (vs Electron version)

- [ ] Open file(s); large file tail-first or last 200k lines.
- [ ] Paste from clipboard → new tab.
- [ ] Tail file (watch for changes, append new lines).
- [ ] Configurable format (plain regex / JSON).
- [ ] Filter by level, time range, search (text/regex).
- [ ] Follow mode (auto-scroll on tail).
- [ ] Stack trace expand/collapse.
- [ ] Multiple tabs.
- [ ] Export filtered view.
- [ ] Bookmarks (add, list, go to).
- [ ] Level colors and search highlight in list.

---

## 13. Suggested order of implementation

1. Project + build, empty Stage with TabPane and one “Welcome” tab.
2. `LogEntry`, `LogParser` (plain only), load one file into `ObservableList<LogEntry>` and show in TableView with columns and level styling.
3. Toolbar: level filter + search; bind to FilteredList.
4. Open file (FileChooser), new tab per file; paste → new tab.
5. Format config (dialog + persistence); optional JSON parser.
6. Tail: WatchService + append; Follow checkbox and scroll.
7. Time range filter; export; bookmarks (model, persistence, UI, go-to).
8. Polish: stack trace expansion, search highlight in cells, jpackage.

This outline should be enough to implement a JavaFX version that matches the current Electron app’s behavior and stays within a single codebase and deployment story (e.g. one JAR or one native installer).
