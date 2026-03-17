import React from 'react';

interface ToolbarProps {
  onPaste: () => void;
  onFormatConfig: () => void;
  levelFilter: string;
  onLevelFilterChange: (v: string) => void;
  search: string;
  onSearchChange: (v: string) => void;
  regexSearch: boolean;
  onRegexSearchChange: (v: boolean) => void;
  followTail: boolean;
  onFollowTailChange: (v: boolean) => void;
  onExport: () => void;
  onBookmarks: () => void;
  timeFrom: string;
  timeTo: string;
  onTimeFromChange: (v: string) => void;
  onTimeToChange: (v: string) => void;
  hasContent: boolean;
  tailing: boolean;
  canTail: boolean;
  onStartTail: () => void;
  onStopTail: () => void;
}

const LEVELS = ['', 'TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'FATAL'];

export function Toolbar({
  onPaste,
  onFormatConfig,
  levelFilter,
  onLevelFilterChange,
  search,
  onSearchChange,
  regexSearch,
  onRegexSearchChange,
  followTail,
  onFollowTailChange,
  onExport,
  onBookmarks,
  timeFrom,
  timeTo,
  onTimeFromChange,
  onTimeToChange,
  hasContent,
  tailing,
  canTail,
  onStartTail,
  onStopTail,
}: ToolbarProps) {
  return (
    <div className="toolbar">
      <div className="toolbar-row">
        <button type="button" onClick={onPaste}>
          Paste
        </button>
        {canTail && (
          tailing ? (
            <button type="button" onClick={onStopTail}>Stop tail</button>
          ) : (
            <button type="button" onClick={onStartTail}>Tail file</button>
          )
        )}
        <button type="button" onClick={onFormatConfig} title="Configure log format">
          Format
        </button>
        <button type="button" onClick={onExport} disabled={!hasContent}>
          Export
        </button>
        <button type="button" onClick={onBookmarks}>
          Bookmarks
        </button>
      </div>
      <div className="toolbar-row">
        <select
          value={levelFilter}
          onChange={(e) => onLevelFilterChange(e.target.value)}
          title="Filter by level"
        >
          {LEVELS.map((l) => (
            <option key={l || '_all'} value={l}>
              {l || 'All levels'}
            </option>
          ))}
        </select>
        <input
          type="text"
          placeholder="Search (regex optional)"
          value={search}
          onChange={(e) => onSearchChange(e.target.value)}
          className="search-input"
        />
        <label className="checkbox-label">
          <input
            type="checkbox"
            checked={regexSearch}
            onChange={(e) => onRegexSearchChange(e.target.checked)}
          />
          Regex
        </label>
        <label className="checkbox-label">
          <input
            type="checkbox"
            checked={followTail}
            onChange={(e) => onFollowTailChange(e.target.checked)}
            disabled={!tailing}
          />
          Follow
        </label>
        <span className="time-filters">
          <input
            type="text"
            placeholder="From (e.g. 2024-01-15 10:00)"
            value={timeFrom}
            onChange={(e) => onTimeFromChange(e.target.value)}
            className="time-input"
          />
          <input
            type="text"
            placeholder="To"
            value={timeTo}
            onChange={(e) => onTimeToChange(e.target.value)}
            className="time-input"
          />
        </span>
      </div>
    </div>
  );
}
