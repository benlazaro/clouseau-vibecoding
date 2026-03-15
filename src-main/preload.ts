import { contextBridge, ipcRenderer } from 'electron';

contextBridge.exposeInMainWorld('logViewerApi', {
  openFile: () => ipcRenderer.invoke('open-file'),
  openFileStream: () => ipcRenderer.invoke('open-file-stream'),
  tailFile: (filePath: string) => ipcRenderer.invoke('tail-file', filePath),
  stopTail: (filePath: string) => ipcRenderer.invoke('stop-tail', filePath),
  saveExport: (filePath: string, content: string) => ipcRenderer.invoke('save-export', filePath, content),
  showSaveDialog: () => ipcRenderer.invoke('show-save-dialog'),
  onTailUpdate: (callback: (filePath: string, chunk: string) => void) => {
    ipcRenderer.on('tail-update', (_, filePath: string, chunk: string) => callback(filePath, chunk));
  },
});
