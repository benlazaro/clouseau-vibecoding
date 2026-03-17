# Clouseau Log Viewer

Desktop log viewer for **Log4j2** logs. Supports local files, live tail, and pasted content. Runs fully offline.

## Features

- **Open file** – Load one or more log files from disk (up to 1GB; very large files show the last 200k lines)
- **Paste** – Paste log content from clipboard into a new tab
- **Tail** – Watch a file for changes (after opening, use “Tail file” on that tab)
- **Configurable format** – Plain text (regex) or JSON; default pattern matches common Log4j2 layout
- **Filter by level** – TRACE, DEBUG, INFO, WARN, ERROR, FATAL
- **Search** – Text or regex over log content
- **Time range** – Filter by timestamp (from/to)
- **Follow** – Auto-scroll to bottom when tailing
- **Stack traces** – Expand/collapse per entry
- **Multiple files** – Tabs for each opened/pasted source
- **Export** – Save filtered view to a file
- **Bookmarks** – Star lines and jump back from the Bookmarks panel

## Requirements

- Node.js 18+
- Windows, macOS, or Linux

## Install and run

```bash
npm install
npm run build
npm start
```

## Development

```bash
npm install
npm run dev
```

This builds the main process, starts the Vite dev server, and launches Electron (loads the app from `http://localhost:5173`).

## Project layout

- `src-main/` – Electron main process (file open, tail, save dialog, IPC)
- `src-renderer/` – React UI (toolbar, tabs, virtualized log list, modals)
- `src-renderer/lib/` – Log parsing (plain regex + JSON), storage helpers
- Log format and bookmarks are stored in the app’s local storage (per machine).

## Log format

Default plain pattern expects lines like:

```
2024-01-15 10:30:45,123 INFO  [main] com.foo.Bar - Message here
```

You can change the regex and JSON field names in **Format** (toolbar). Patterns use capture groups: 1 = timestamp, 2 = level, 3 = optional thread, 4 = logger, 5 = message.
