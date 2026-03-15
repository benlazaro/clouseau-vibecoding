import React, { useState, useEffect } from 'react';
import type { LogFormatConfig, PlainFormatConfig, JsonFormatConfig } from '@/types/global';
import { DEFAULT_FORMAT } from '@/lib/logParser';

interface FormatConfigModalProps {
  initial: LogFormatConfig | null;
  onSave: (config: LogFormatConfig) => void;
  onClose: () => void;
}

export function FormatConfigModal({ initial, onSave, onClose }: FormatConfigModalProps) {
  const [kind, setKind] = useState<'plain' | 'json'>(initial?.kind ?? 'plain');
  const [pattern, setPattern] = useState(
    (initial?.kind === 'plain' ? (initial as PlainFormatConfig).pattern : DEFAULT_FORMAT.pattern) ?? ''
  );
  const [patternHint, setPatternHint] = useState(
    (initial?.kind === 'plain' ? (initial as PlainFormatConfig).patternHint : '') ?? ''
  );
  const [levelKey, setLevelKey] = useState(
    (initial?.kind === 'json' ? (initial as JsonFormatConfig).levelKey : 'level') ?? 'level'
  );
  const [timestampKey, setTimestampKey] = useState(
    (initial?.kind === 'json' ? (initial as JsonFormatConfig).timestampKey : 'timestamp') ?? 'timestamp'
  );
  const [messageKey, setMessageKey] = useState(
    (initial?.kind === 'json' ? (initial as JsonFormatConfig).messageKey : 'message') ?? 'message'
  );
  const [loggerKey, setLoggerKey] = useState(
    (initial?.kind === 'json' ? (initial as JsonFormatConfig).loggerKey : 'logger') ?? 'logger'
  );

  useEffect(() => {
    if (initial?.kind === 'plain') {
      setPattern((initial as PlainFormatConfig).pattern);
      setPatternHint((initial as PlainFormatConfig).patternHint ?? '');
    } else if (initial?.kind === 'json') {
      const j = initial as JsonFormatConfig;
      setLevelKey(j.levelKey ?? 'level');
      setTimestampKey(j.timestampKey ?? 'timestamp');
      setMessageKey(j.messageKey ?? 'message');
      setLoggerKey(j.loggerKey ?? 'logger');
    }
  }, [initial]);

  const handleSave = () => {
    if (kind === 'plain') {
      onSave({ kind: 'plain', pattern: pattern || DEFAULT_FORMAT.pattern, patternHint: patternHint || undefined });
    } else {
      onSave({
        kind: 'json',
        levelKey: levelKey || 'level',
        timestampKey: timestampKey || 'timestamp',
        messageKey: messageKey || 'message',
        loggerKey: loggerKey || 'logger',
      });
    }
    onClose();
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h2>Log format</h2>
        <p className="modal-hint">Configure how log lines are parsed (Log4j2 plain or JSON).</p>
        <div className="format-kind">
          <label>
            <input
              type="radio"
              name="kind"
              checked={kind === 'plain'}
              onChange={() => setKind('plain')}
            />
            Plain text (regex)
          </label>
          <label>
            <input
              type="radio"
              name="kind"
              checked={kind === 'json'}
              onChange={() => setKind('json')}
            />
            JSON
          </label>
        </div>
        {kind === 'plain' && (
          <>
            <label>
              Regex pattern (groups: 1=timestamp, 2=level, 3=logger optional, 4=message)
              <input
                type="text"
                value={pattern}
                onChange={(e) => setPattern(e.target.value)}
                placeholder="e.g. ^(\d{4}-...)\s+(\w+)\s+..."
              />
            </label>
            <label>
              Pattern hint (for display)
              <input
                type="text"
                value={patternHint}
                onChange={(e) => setPatternHint(e.target.value)}
                placeholder="e.g. %d %-5p %c - %m%n"
              />
            </label>
          </>
        )}
        {kind === 'json' && (
          <>
            <label>Level key <input value={levelKey} onChange={(e) => setLevelKey(e.target.value)} /></label>
            <label>Timestamp key <input value={timestampKey} onChange={(e) => setTimestampKey(e.target.value)} /></label>
            <label>Message key <input value={messageKey} onChange={(e) => setMessageKey(e.target.value)} /></label>
            <label>Logger key <input value={loggerKey} onChange={(e) => setLoggerKey(e.target.value)} /></label>
          </>
        )}
        <div className="modal-actions">
          <button type="button" onClick={onClose}>Cancel</button>
          <button type="button" className="primary" onClick={handleSave}>Save</button>
        </div>
      </div>
    </div>
  );
}
