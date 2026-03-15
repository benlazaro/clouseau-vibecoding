import React, { useCallback, useMemo } from 'react';
import { Virtuoso } from 'react-virtuoso';
import type { LogEntry } from '@/types/global';

interface LogListProps {
  entries: LogEntry[];
  bookmarkedLines: Set<number>;
  onToggleBookmark: (lineNumber: number) => void;
  searchHighlight?: RegExp | null;
  followTail: boolean;
  atBottomRef: React.MutableRefObject<(() => void) | null>;
}

const levelClass = (level: string) => (level ? `level-${level.toLowerCase()}` : '');

function LogRow({
  entry,
  isBookmarked,
  onToggleBookmark,
  searchHighlight,
}: {
  entry: LogEntry;
  isBookmarked: boolean;
  onToggleBookmark: (lineNumber: number) => void;
  searchHighlight?: RegExp | null;
}) {
  const [expanded, setExpanded] = React.useState(false);
  const hasStack = !!entry.stackTrace;

  const messagePart = useMemo(() => {
    if (!searchHighlight) return entry.message;
    try {
      return entry.message.replace(
        searchHighlight,
        (m) => `<mark class="search-highlight">${m}</mark>`
      );
    } catch {
      return entry.message;
    }
  }, [entry.message, searchHighlight]);

  return (
    <div
      className={`log-row ${levelClass(entry.level)} ${isBookmarked ? 'bookmarked' : ''}`}
      data-line={entry.lineNumber}
    >
      <div className="log-row-main">
        <button
          type="button"
          className="bookmark-btn"
          onClick={() => onToggleBookmark(entry.lineNumber)}
          title={isBookmarked ? 'Remove bookmark' : 'Add bookmark'}
        >
          {isBookmarked ? '★' : '☆'}
        </button>
        <span className="log-meta">
          {entry.timestamp && <span className="log-ts">{entry.timestamp}</span>}
          {entry.level && <span className="log-level">{entry.level}</span>}
          {entry.logger && <span className="log-logger">{entry.logger}</span>}
        </span>
        <span
          className="log-message"
          dangerouslySetInnerHTML={{ __html: messagePart }}
        />
        {hasStack && (
          <button
            type="button"
            className="expand-btn"
            onClick={() => setExpanded((e) => !e)}
          >
            {expanded ? '−' : '+'}
          </button>
        )}
      </div>
      {hasStack && expanded && (
        <pre className="log-stack">{entry.stackTrace}</pre>
      )}
    </div>
  );
}

export function LogList({
  entries,
  bookmarkedLines,
  onToggleBookmark,
  searchHighlight,
  followTail,
  atBottomRef,
}: LogListProps) {
  const virtuosoRef = React.useRef(null);
  const followRef = React.useRef(followTail);
  followRef.current = followTail;

  const atBottom = useCallback(() => {
    if (followRef.current && virtuosoRef.current) {
      (virtuosoRef.current as { scrollToIndex: (opts: { index: number }) => void }).scrollToIndex({
        index: entries.length - 1,
      });
    }
  }, [entries.length]);

  React.useEffect(() => {
    atBottomRef.current = atBottom;
  }, [atBottom, atBottomRef]);

  const Item = useCallback(
    (index: number) => {
      const entry = entries[index];
      if (!entry) return null;
      return (
        <LogRow
          entry={entry}
          isBookmarked={bookmarkedLines.has(entry.lineNumber)}
          onToggleBookmark={onToggleBookmark}
          searchHighlight={searchHighlight}
        />
      );
    },
    [entries, bookmarkedLines, onToggleBookmark, searchHighlight]
  );

  if (entries.length === 0) {
    return (
      <div className="log-list empty">
        <p>Open a log file, paste content, or use &quot;Open file&quot; to get started.</p>
      </div>
    );
  }

  return (
    <div className="log-list">
      <Virtuoso
        ref={virtuosoRef}
        style={{ height: '100%' }}
        totalCount={entries.length}
        itemContent={Item}
        followOutput={followTail ? 'smooth' : false}
      />
    </div>
  );
}
