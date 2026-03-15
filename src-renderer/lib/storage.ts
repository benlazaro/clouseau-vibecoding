import type { LogFormatConfig } from '@/types/global';

const FORMAT_KEY = 'log4j2-viewer-format';
const BOOKMARKS_KEY = 'log4j2-viewer-bookmarks';

export function loadFormatConfig(): LogFormatConfig | null {
  try {
    const s = localStorage.getItem(FORMAT_KEY);
    if (!s) return null;
    return JSON.parse(s) as LogFormatConfig;
  } catch {
    return null;
  }
}

export function saveFormatConfig(config: LogFormatConfig): void {
  localStorage.setItem(FORMAT_KEY, JSON.stringify(config));
}

export interface StoredBookmark {
  tabId: string;
  lineNumber: number;
  label?: string;
  createdAt: number;
}

export function loadBookmarks(): StoredBookmark[] {
  try {
    const s = localStorage.getItem(BOOKMARKS_KEY);
    if (!s) return [];
    return JSON.parse(s) as StoredBookmark[];
  } catch {
    return [];
  }
}

export function saveBookmarks(bookmarks: StoredBookmark[]): void {
  localStorage.setItem(BOOKMARKS_KEY, JSON.stringify(bookmarks));
}
