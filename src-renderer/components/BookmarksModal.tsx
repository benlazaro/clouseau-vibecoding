import React from 'react';
import type { StoredBookmark } from '@/lib/storage';

interface BookmarksModalProps {
  bookmarks: StoredBookmark[];
  tabLabels: Record<string, string>;
  onRemove: (createdAt: number) => void;
  onGoTo: (tabId: string, lineNumber: number) => void;
  onClose: () => void;
}

export function BookmarksModal({
  bookmarks,
  tabLabels,
  onRemove,
  onGoTo,
  onClose,
}: BookmarksModalProps) {
  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h2>Bookmarks</h2>
        {bookmarks.length === 0 ? (
          <p className="muted">No bookmarks. Use the star on a log line to add one.</p>
        ) : (
          <ul className="bookmark-list">
            {bookmarks.map((b) => (
              <li key={b.createdAt}>
                <button
                  type="button"
                  className="primary"
                  onClick={() => {
                    onGoTo(b.tabId, b.lineNumber);
                    onClose();
                  }}
                >
                  {tabLabels[b.tabId] ?? b.tabId} : {b.lineNumber}
                </button>
                {b.label && <span className="bookmark-label">{b.label}</span>}
                <button type="button" onClick={() => onRemove(b.createdAt)}>Remove</button>
              </li>
            ))}
          </ul>
        )}
        <div className="modal-actions">
          <button type="button" onClick={onClose}>Close</button>
        </div>
      </div>
    </div>
  );
}
