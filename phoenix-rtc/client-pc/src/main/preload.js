const { contextBridge, ipcRenderer } = require('electron');

/**
 * 预加载脚本 - 使用 electron-toolkit
 * 在渲染进程和主进程之间建立安全的桥梁
 */

// 使用 electron-toolkit/preload 提供安全的 IPC 桥梁
contextBridge.exposeInMainWorld('electronAPI', {
  // 获取应用版本
  getVersion: () => ipcRenderer.invoke('get-version'),

  // 打开外部链接
  openExternal: (url) => ipcRenderer.invoke('open-external', url),

  // 显示通知
  showNotification: (title, body) =>
    ipcRenderer.invoke('show-notification', title, body),

  // 监听来电通知（从主进程）
  onIncomingCall: (callback) => {
    const handler = (event, invite) => callback(invite);
    ipcRenderer.on('incoming-call', handler);
    return () => ipcRenderer.removeListener('incoming-call', handler);
  },

  // 监听窗口状态变化
  onWindowFocus: (callback) => {
    ipcRenderer.on('window-focus', callback);
    return () => ipcRenderer.removeListener('window-focus', callback);
  },

  onWindowBlur: (callback) => {
    ipcRenderer.on('window-blur', callback);
    return () => ipcRenderer.removeListener('window-blur', callback);
  },
});
