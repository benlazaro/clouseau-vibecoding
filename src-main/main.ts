import { app, BrowserWindow, ipcMain, dialog, Menu, type MenuItemConstructorOptions } from 'electron';
import * as path from 'path';
import * as fs from 'fs';
import * as readline from 'readline';
import { watch } from 'chokidar';

const CHUNK_SIZE_BYTES = 10 * 1024 * 1024; // 10MB for large files
const MAX_LINES_TAIL = 200_000;
const MAX_FILE_SIZE_GB = 1;

let mainWindow: BrowserWindow | null = null;
const watchers = new Map<string, ReturnType<typeof watch>>();

function buildMenu() {
  const isMac = process.platform === 'darwin';
  const template: MenuItemConstructorOptions[] = [
    ...(isMac
      ? ([
          {
            label: app.name,
            submenu: [{ role: 'about' }, { type: 'separator' }, { role: 'quit' }],
          } satisfies MenuItemConstructorOptions,
        ] as MenuItemConstructorOptions[])
      : []),
    {
      label: 'File',
      submenu: [
        {
          label: 'Open…',
          accelerator: 'CmdOrCtrl+O',
          click: () => {
            mainWindow?.webContents.send('menu-open-file');
          },
        },
        {
          label: 'Close Tab',
          accelerator: 'CmdOrCtrl+W',
          click: () => {
            mainWindow?.webContents.send('menu-close-tab');
          },
        },
        { type: 'separator' as const },
        (isMac ? { role: 'close' as const } : { role: 'quit' as const }) as MenuItemConstructorOptions,
      ],
    },
    {
      label: 'View',
      submenu: [
        { role: 'reload' as const },
        { role: 'toggleDevTools' as const },
        { type: 'separator' as const },
        { role: 'resetZoom' as const },
        { role: 'zoomIn' as const },
        { role: 'zoomOut' as const },
        { type: 'separator' as const },
        { role: 'togglefullscreen' as const },
      ] satisfies MenuItemConstructorOptions[],
    },
    {
      label: 'Window',
      submenu: [
        { role: 'minimize' as const },
        { role: 'zoom' as const },
        ...(isMac ? ([{ type: 'separator' as const }, { role: 'front' as const }] satisfies MenuItemConstructorOptions[]) : []),
      ] satisfies MenuItemConstructorOptions[],
    },
  ];

  const menu = Menu.buildFromTemplate(template);
  Menu.setApplicationMenu(menu);
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1200,
    height: 800,
    title: 'Clouseau Log Viewer',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });

  buildMenu();

  const isDev = process.env.NODE_ENV === 'development';
  if (isDev) {
    mainWindow.loadURL('http://localhost:5173');
    mainWindow.webContents.openDevTools();
  } else {
    mainWindow.loadFile(path.join(__dirname, '../dist-renderer/index.html'));
  }
}

app.whenReady().then(createWindow);

app.on('window-all-closed', () => {
  watchers.forEach((w) => w.close());
  watchers.clear();
  app.quit();
});

// Open file(s) – returns content in chunks for large files
ipcMain.handle('open-file', async (): Promise<{ path: string; chunks: string[]; totalLines: number; truncated: boolean } | { error: string }> => {
  if (!mainWindow) return { error: 'No window' };
  const result = await dialog.showOpenDialog(mainWindow, {
    properties: ['openFile', 'multiSelections'],
    filters: [{ name: 'Log files', extensions: ['log', 'txt', 'out'] }, { name: 'All', extensions: ['*'] }],
  });
  if (result.canceled || result.filePaths.length === 0) return { error: 'Canceled' };
  const filePath = result.filePaths[0];
  const stat = fs.statSync(filePath);
  if (stat.size > MAX_FILE_SIZE_GB * 1024 * 1024 * 1024) return { error: 'File exceeds 1GB limit' };
  try {
    const content = fs.readFileSync(filePath, 'utf-8');
    const lines = content.split(/\r?\n/);
    const totalLines = lines.length;
    let truncated = false;
    let sendLines = lines;
    if (lines.length > MAX_LINES_TAIL) {
      sendLines = lines.slice(-MAX_LINES_TAIL);
      truncated = true;
    }
    return { path: filePath, chunks: [sendLines.join('\n')], totalLines, truncated };
  } catch (e: unknown) {
    return { error: e instanceof Error ? e.message : String(e) };
  }
});

// Stream file from end (for very large files – tail first chunk)
ipcMain.handle(
  'open-file-stream',
  async (): Promise<
    | { path: string; chunks: string[]; totalLinesApprox: number; truncated: boolean }
    | { error: string }
  > => {
    if (!mainWindow) return { error: 'No window' };
    const result = await dialog.showOpenDialog(mainWindow, {
      properties: ['openFile'],
      filters: [{ name: 'Log files', extensions: ['log', 'txt', 'out'] }, { name: 'All', extensions: ['*'] }],
    });
    if (result.canceled || result.filePaths.length === 0) return { error: 'Canceled' };
    const filePath = result.filePaths[0];
    const stat = fs.statSync(filePath);
    if (stat.size > MAX_FILE_SIZE_GB * 1024 * 1024 * 1024) return { error: 'File exceeds 1GB limit' };
    try {
      const fd = fs.openSync(filePath, 'r');
      const buffer = Buffer.alloc(Math.min(CHUNK_SIZE_BYTES, stat.size));
      const start = Math.max(0, stat.size - buffer.length);
      fs.readSync(fd, buffer, 0, buffer.length, start);
      fs.closeSync(fd);
      const tailText = buffer.toString('utf-8');
      const lines = tailText.split(/\r?\n/).filter((l) => l.length > 0);
      const truncated = stat.size > CHUNK_SIZE_BYTES;
      return {
        path: filePath,
        chunks: [lines.join('\n')],
        totalLinesApprox: truncated ? -1 : lines.length,
        truncated,
      };
    } catch (e: unknown) {
      return { error: e instanceof Error ? e.message : String(e) };
    }
  }
);

// Watch file for tail (live updates)
ipcMain.handle('tail-file', async (_, filePath: string): Promise<{ error?: string }> => {
  if (watchers.has(filePath)) return {};
  try {
    const w = watch(filePath, { persistent: true });
    w.on('change', () => {
      try {
        const content = fs.readFileSync(filePath, 'utf-8');
        const lines = content.split(/\r?\n/);
        const tail = lines.length > 500 ? lines.slice(-500) : lines;
        mainWindow?.webContents.send('tail-update', filePath, tail.join('\n'));
      } catch {
        // ignore read errors
      }
    });
    watchers.set(filePath, w);
    return {};
  } catch (e: unknown) {
    return { error: e instanceof Error ? e.message : String(e) };
  }
});

ipcMain.handle('stop-tail', (_, filePath: string) => {
  const w = watchers.get(filePath);
  if (w) {
    w.close();
    watchers.delete(filePath);
  }
});

// Save/export selected or filtered content
ipcMain.handle('save-export', async (_, filePath: string, content: string): Promise<{ error?: string }> => {
  try {
    fs.writeFileSync(filePath, content, 'utf-8');
    return {};
  } catch (e: unknown) {
    return { error: e instanceof Error ? e.message : String(e) };
  }
});

ipcMain.handle('show-save-dialog', async (): Promise<{ path?: string; error?: string }> => {
  if (!mainWindow) return { error: 'No window' };
  const result = await dialog.showSaveDialog(mainWindow, {
    defaultPath: 'export.log',
    filters: [{ name: 'Log files', extensions: ['log', 'txt'] }],
  });
  if (result.canceled || !result.filePath) return { error: 'Canceled' };
  return { path: result.filePath };
});
