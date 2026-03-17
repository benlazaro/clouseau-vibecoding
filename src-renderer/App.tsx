import React, { useState, useCallback, useMemo, useRef, useEffect } from 'react';
import { Toolbar } from '@/components/Toolbar';
import { LogList } from '@/components/LogList';
import { FormatConfigModal } from '@/components/FormatConfigModal';
import { BookmarksModal } from '@/components/BookmarksModal';
import { parseLines, DEFAULT_FORMAT } from '@/lib/logParser';
import { loadFormatConfig, saveFormatConfig, loadBookmarks, saveBookmarks, type StoredBookmark } from '@/lib/storage';
import type { LogEntry, LogFormatConfig, LogLevel } from '@/types/global';

interface Tab {
  id: string;
  label: string;
  entries: LogEntry[];
  filePath?: string;
  tailing: boolean;
}

function filterEntries(
  entries: LogEntry[],
  levelFilter: LogLevel | '',
  search: string,
  regexSearch: boolean,
  timeFrom: string,
  timeTo: string
): LogEntry[] {
  let out = entries;
  if (levelFilter) {
    out = out.filter((e) => e.level === levelFilter);
  }
  if (timeFrom.trim()) {
    const from = timeFrom.trim();
    out = out.filter((e) => e.timestamp >= from || !e.timestamp);
  }
  if (timeTo.trim()) {
    const to = timeTo.trim();
    out = out.filter((e) => e.timestamp <= to || !e.timestamp);
  }
  if (search.trim()) {
    try {
      const re = regexSearch ? new RegExp(search, 'i') : new RegExp(escapeRe(search.trim()), 'i');
      out = out.filter((e) => re.test(e.raw) || re.test(e.message));
    } catch {
      const q = search.trim().toLowerCase();
      out = out.filter((e) => e.raw.toLowerCase().includes(q) || e.message.toLowerCase().includes(q));
    }
  }
  return out;
}

function escapeRe(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

export default function App() {
  const [tabs, setTabs] = useState<Tab[]>([]);
  const [activeTabId, setActiveTabId] = useState<string | null>(null);
  const [formatConfig, setFormatConfigState] = useState<LogFormatConfig | null>(() => loadFormatConfig());
  const formatConfigResolved = formatConfig ?? DEFAULT_FORMAT;

  const [levelFilter, setLevelFilter] = useState<LogLevel | ''>('');
  const [search, setSearch] = useState('');
  const [regexSearch, setRegexSearch] = useState(false);
  const [followTail, setFollowTail] = useState(true);
  const [timeFrom, setTimeFrom] = useState('');
  const [timeTo, setTimeTo] = useState('');
  const [showFormatModal, setShowFormatModal] = useState(false);
  const [showBookmarksModal, setShowBookmarksModal] = useState(false);
  const [bookmarks, setBookmarks] = useState<StoredBookmark[]>(() => loadBookmarks());
  const atBottomRef = useRef<(() => void) | null>(null);

  const addBookmark = useCallback((tabId: string, lineNumber: number) => {
    setBookmarks((prev) => {
      const exists = prev.some((b) => b.tabId === tabId && b.lineNumber === lineNumber);
      if (exists) return prev.filter((b) => !(b.tabId === tabId && b.lineNumber === lineNumber));
      return [...prev, { tabId, lineNumber, createdAt: Date.now() }];
    });
  }, []);

  useEffect(() => {
    saveBookmarks(bookmarks);
  }, [bookmarks]);

  const removeBookmark = useCallback((createdAt: number) => {
    setBookmarks((prev) => prev.filter((b) => b.createdAt !== createdAt));
  }, []);

  const activeTab = tabs.find((t) => t.id === activeTabId);
  const activeEntries = activeTab?.entries ?? [];
  const filteredEntries = useMemo(
    () =>
      filterEntries(
        activeEntries,
        levelFilter,
        search,
        regexSearch,
        timeFrom,
        timeTo
      ),
    [activeEntries, levelFilter, search, regexSearch, timeFrom, timeTo]
  );

  const searchHighlight = useMemo(() => {
    if (!search.trim()) return null;
    try {
      return regexSearch ? new RegExp(`(${search})`, 'gi') : new RegExp(`(${escapeRe(search.trim())})`, 'gi');
    } catch {
      return new RegExp(`(${escapeRe(search.trim())})`, 'gi');
    }
  }, [search, regexSearch]);

  const bookmarkedLines = useMemo(() => {
    if (!activeTabId) return new Set<number>();
    return new Set(bookmarks.filter((b) => b.tabId === activeTabId).map((b) => b.lineNumber));
  }, [activeTabId, bookmarks]);

  const toggleBookmark = useCallback(
    (lineNumber: number) => {
      if (activeTabId) addBookmark(activeTabId, lineNumber);
    },
    [activeTabId, addBookmark]
  );

  const setFormatConfig = useCallback((config: LogFormatConfig) => {
    setFormatConfigState(config);
    saveFormatConfig(config);
  }, []);

  const openFile = useCallback(async () => {
    const result = await window.logViewerApi.openFile();
    if ('error' in result) {
      if (result.error !== 'Canceled') alert(result.error);
      return;
    }
    const allEntries: LogEntry[] = [];
    let id = 0;
    for (const chunk of result.chunks) {
      const entries = parseLines(chunk, formatConfigResolved, id);
      id += entries.length;
      allEntries.push(...entries);
    }
    const tab: Tab = {
      id: `file-${Date.now()}`,
      label: result.path.split(/[/\\]/).pop() ?? 'Log',
      entries: allEntries,
      filePath: result.path,
      tailing: false,
    };
    setTabs((prev) => [...prev, tab]);
    setActiveTabId(tab.id);
  }, [formatConfigResolved]);

  useEffect(() => {
    window.logViewerApi?.onMenuOpenFile?.(() => {
      openFile();
    });
  }, [openFile]);

  const pasteBuffer = useCallback(() => {
    const label = prompt('Tab name (optional)', 'Pasted log');
    const tab: Tab = {
      id: `paste-${Date.now()}`,
      label: label || 'Pasted log',
      entries: [],
      tailing: false,
    };
    navigator.clipboard.readText().then((text) => {
      const entries = parseLines(text, formatConfigResolved, 0);
      setTabs((prev) => {
        const next = [...prev, { ...tab, entries }];
        setActiveTabId(tab.id);
        return next;
      });
    }).catch(() => alert('Could not read clipboard'));
  }, [formatConfigResolved]);

  const startTail = useCallback(async () => {
    if (!activeTab?.filePath) return;
    const err = await window.logViewerApi.tailFile(activeTab.filePath);
    if (err.error) alert(err.error);
    else
      setTabs((prev) =>
        prev.map((t) => (t.id === activeTabId ? { ...t, tailing: true } : t))
      );
  }, [activeTab, activeTabId]);

  const stopTail = useCallback(() => {
    if (!activeTab?.filePath) return;
    window.logViewerApi.stopTail(activeTab.filePath);
    setTabs((prev) =>
      prev.map((t) => (t.id === activeTabId ? { ...t, tailing: false } : t))
    );
  }, [activeTab, activeTabId]);

  useEffect(() => {
    if (!window.logViewerApi?.onTailUpdate) return;
    window.logViewerApi.onTailUpdate((filePath: string, chunk: string) => {
      setTabs((prev) => {
        const tab = prev.find((t) => t.filePath === filePath);
        if (!tab) return prev;
        const newEntries = parseLines(chunk, formatConfigResolved, tab.entries.length);
        const merged = newEntries.length ? [...tab.entries, ...newEntries].slice(-100_000) : tab.entries;
        return prev.map((t) => (t.filePath === filePath ? { ...t, entries: merged } : t));
      });
    });
  }, [formatConfigResolved]);

  const exportFiltered = useCallback(async () => {
    const pathResult = await window.logViewerApi.showSaveDialog();
    if (pathResult.error || !pathResult.path) return;
    const content = filteredEntries.map((e) => e.raw).join('\n');
    const err = await window.logViewerApi.saveExport(pathResult.path, content);
    if (err.error) alert(err.error);
  }, [filteredEntries]);

  const goToBookmark = useCallback((tabId: string, lineNumber: number) => {
    setActiveTabId(tabId);
    setTimeout(() => {
      const el = document.querySelector(`[data-line="${lineNumber}"]`);
      el?.scrollIntoView({ behavior: 'smooth' });
    }, 100);
  }, []);

  const tabLabels = useMemo(() => Object.fromEntries(tabs.map((t) => [t.id, t.label])), [tabs]);

  useEffect(() => {
    atBottomRef.current?.();
  }, [filteredEntries.length]);

  return (
    <>
      <div className="app">
        <div className="tabs">
          {tabs.map((t) => (
            <button
              key={t.id}
              type="button"
              className={`tab ${t.id === activeTabId ? 'active' : ''}`}
              onClick={() => setActiveTabId(t.id)}
            >
              {t.label}
            </button>
          ))}
        </div>
        <Toolbar
          onPaste={pasteBuffer}
          onFormatConfig={() => setShowFormatModal(true)}
          levelFilter={levelFilter}
          onLevelFilterChange={(v) => setLevelFilter(v as LogLevel | '')}
          search={search}
          onSearchChange={setSearch}
          regexSearch={regexSearch}
          onRegexSearchChange={setRegexSearch}
          followTail={followTail}
          onFollowTailChange={setFollowTail}
          onExport={exportFiltered}
          onBookmarks={() => setShowBookmarksModal(true)}
          timeFrom={timeFrom}
          timeTo={timeTo}
          onTimeFromChange={setTimeFrom}
          onTimeToChange={setTimeTo}
          hasContent={filteredEntries.length > 0}
          tailing={activeTab?.tailing ?? false}
          canTail={!!activeTab?.filePath}
          onStartTail={startTail}
          onStopTail={stopTail}
        />
        <div className="content">
          <LogList
            entries={filteredEntries}
            bookmarkedLines={bookmarkedLines}
            onToggleBookmark={toggleBookmark}
            searchHighlight={searchHighlight}
            followTail={followTail}
            atBottomRef={atBottomRef}
          />
        </div>
      </div>
      {showFormatModal && (
        <FormatConfigModal
          initial={formatConfig}
          onSave={setFormatConfig}
          onClose={() => setShowFormatModal(false)}
        />
      )}
      {showBookmarksModal && (
        <BookmarksModal
          bookmarks={bookmarks}
          tabLabels={tabLabels}
          onRemove={removeBookmark}
          onGoTo={goToBookmark}
          onClose={() => setShowBookmarksModal(false)}
        />
      )}
    </>
  );
}
