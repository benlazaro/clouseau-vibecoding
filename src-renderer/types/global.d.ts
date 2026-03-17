export interface LogViewerApi {
  openFile: () => Promise<
    | { path: string; chunks: string[]; totalLines: number; truncated: boolean }
    | { error: string }
  >;
  openFileStream: () => Promise<
    | { path: string; chunks: string[]; totalLinesApprox: number; truncated: boolean }
    | { error: string }
  >;
  tailFile: (filePath: string) => Promise<{ error?: string }>;
  stopTail: (filePath: string) => void;
  saveExport: (filePath: string, content: string) => Promise<{ error?: string }>;
  showSaveDialog: () => Promise<{ path?: string; error?: string }>;
  onTailUpdate: (callback: (filePath: string, chunk: string) => void) => void;
  onMenuOpenFile: (callback: () => void) => void;
}

declare global {
  interface Window {
    logViewerApi: LogViewerApi;
  }
}

export type LogLevel = 'TRACE' | 'DEBUG' | 'INFO' | 'WARN' | 'ERROR' | 'FATAL' | '';

export interface LogEntry {
  id: number;
  raw: string;
  level: LogLevel;
  timestamp: string;
  logger?: string;
  message: string;
  stackTrace?: string;
  lineNumber: number;
}

export type LogFormatKind = 'plain' | 'json';

export interface PlainFormatConfig {
  kind: 'plain';
  /** Regex with groups: 1=timestamp, 2=level, 3=logger (optional), 4=message. Default Log4j2 pattern. */
  pattern: string;
  /** Example: '%d{ISO8601} %-5p [%t] %c - %m%n' */
  patternHint?: string;
}

export interface JsonFormatConfig {
  kind: 'json';
  /** JSON path for level, e.g. 'level' or '$.level' */
  levelKey?: string;
  timestampKey?: string;
  messageKey?: string;
  loggerKey?: string;
}

export type LogFormatConfig = PlainFormatConfig | JsonFormatConfig;
