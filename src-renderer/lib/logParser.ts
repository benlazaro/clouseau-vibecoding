import type { LogEntry, LogLevel, LogFormatConfig, PlainFormatConfig, JsonFormatConfig } from '@/types/global';

const LEVELS: LogLevel[] = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'FATAL', ''];

// Default Log4j2 pattern: 2024-01-15 10:30:45,123 or 10:30:45.123, then level [thread] logger - message
const DEFAULT_PLAIN_REGEX =
  /^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}[.,]?\d{0,3})\s+(\w+)\s+(?:\[([^\]]*)\]\s+)?([\w.]+)\s+-\s+(.*)$/s;

function parseLevel(s: string): LogLevel {
  const u = s?.toUpperCase();
  return (LEVELS as string[]).includes(u) ? (u as LogLevel) : '';
}

function parsePlainLine(
  line: string,
  lineNumber: number,
  id: number,
  config: PlainFormatConfig
): LogEntry {
  const pattern = config.pattern ? new RegExp(config.pattern, 's') : DEFAULT_PLAIN_REGEX;
  const m = line.match(pattern);
  if (m) {
    const [, timestamp, level, thread, logger, message] = m;
    return {
      id,
      raw: line,
      level: parseLevel(level ?? ''),
      timestamp: (timestamp ?? '').trim(),
      logger: logger?.trim() || undefined,
      message: (message ?? line).trim(),
      lineNumber,
    };
  }
  return {
    id,
    raw: line,
    level: '',
    timestamp: '',
    message: line,
    lineNumber,
  };
}

function parseJsonLine(
  line: string,
  lineNumber: number,
  id: number,
  config: JsonFormatConfig
): LogEntry {
  try {
    const obj = JSON.parse(line) as Record<string, unknown>;
    const levelKey = config.levelKey ?? 'level';
    const timestampKey = config.timestampKey ?? 'timestamp';
    const messageKey = config.messageKey ?? 'message';
    const loggerKey = config.loggerKey ?? 'logger';
    const level = parseLevel(String(obj[levelKey] ?? ''));
    const timestamp = String(obj[timestampKey] ?? '');
    const message = String(obj[messageKey] ?? line);
    const logger = obj[loggerKey] != null ? String(obj[loggerKey]) : undefined;
    return {
      id,
      raw: line,
      level,
      timestamp,
      logger,
      message,
      lineNumber,
    };
  } catch {
    return {
      id,
      raw: line,
      level: '',
      timestamp: '',
      message: line,
      lineNumber,
    };
  }
}

export function parseLine(
  line: string,
  lineNumber: number,
  id: number,
  config: LogFormatConfig
): LogEntry {
  if (config.kind === 'json') return parseJsonLine(line, lineNumber, id, config);
  return parsePlainLine(line, lineNumber, id, config as PlainFormatConfig);
}

export function parseLines(
  text: string,
  config: LogFormatConfig,
  startId = 0
): LogEntry[] {
  const lines = text.split(/\r?\n/);
  const entries: LogEntry[] = [];
  let buffer = '';
  let lineNum = 0;
  let id = startId;

  for (const line of lines) {
    lineNum++;
    const fullLine = buffer ? buffer + '\n' + line : line;
    buffer = '';

    const entry = parseLine(fullLine, lineNum, id++, config);
    const looksLikeContinuation =
      entries.length > 0 &&
      entry.level === '' &&
      (fullLine.trim().startsWith('\t') ||
        fullLine.trim().startsWith('  ') ||
        fullLine.trim().startsWith('at '));
    if (looksLikeContinuation) {
      const prev = entries[entries.length - 1]!;
      prev.stackTrace = prev.stackTrace ? prev.stackTrace + '\n' + fullLine : fullLine;
      continue;
    }
    entries.push(entry);
  }

  return entries;
}

export const DEFAULT_FORMAT: PlainFormatConfig = {
  kind: 'plain',
  pattern: DEFAULT_PLAIN_REGEX.source,
  patternHint: '%d{ISO8601} %-5p [%t] %c - %m%n',
};
