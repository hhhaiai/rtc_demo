const { app, BrowserWindow, ipcMain, Menu, Tray, nativeImage, Notification } = require('electron');
const path = require('path');
const isDev = process.env.NODE_ENV === 'development';

// 使用 electron-toolkit
const { is } = require('@electron-toolkit/utils');

// 保持全局引用，防止垃圾回收
let mainWindow = null;
let tray = null;

/**
 * 创建主窗口
 */
function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1200,
    height: 800,
    minWidth: 800,
    minHeight: 600,
    title: 'Phoenix RTC',
    icon: path.join(__dirname, '../../assets/icon.png'),
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      preload: path.join(__dirname, 'preload.js'),
      sandbox: true, // 启用沙箱增强安全性
    },
  });

  // 加载应用
  if (is.dev) {
    mainWindow.loadURL('http://localhost:3000');
    mainWindow.webContents.openDevTools();
  } else {
    mainWindow.loadFile(path.join(__dirname, '../renderer/index.html'));
  }

  // 窗口关闭事件
  mainWindow.on('close', (event) => {
    if (!app.isQuitting) {
      event.preventDefault();
      mainWindow.hide();
    }
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

/**
 * 创建系统托盘
 */
function createTray() {
  const iconPath = path.join(__dirname, '../../assets/tray-icon.png');
  const icon = nativeImage.createFromPath(iconPath);

  tray = new Tray(icon.resize({ width: 16, height: 16 }));

  const contextMenu = Menu.buildFromTemplate([
    {
      label: '显示窗口',
      click: () => {
        if (mainWindow) {
          mainWindow.show();
          mainWindow.focus();
        }
      },
    },
    {
      label: '退出',
      click: () => {
        app.isQuitting = true;
        app.quit();
      },
    },
  ]);

  tray.setToolTip('Phoenix RTC');
  tray.setContextMenu(contextMenu);

  tray.on('click', () => {
    if (mainWindow) {
      mainWindow.show();
      mainWindow.focus();
    }
  });
}

/**
 * 应用启动
 */
app.whenReady().then(() => {
  createWindow();
  createTray();

  // macOS 特殊处理
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });
});

// 所有窗口关闭时退出应用（非 macOS）
app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

app.on('before-quit', () => {
  app.isQuitting = true;
});

// IPC 通信处理
ipcMain.handle('get-version', () => {
  return app.getVersion();
});

ipcMain.handle('open-external', (event, url) => {
  require('electron').shell.openExternal(url);
});

// 系统通知
ipcMain.handle('show-notification', (event, title, body) => {
  if (Notification.isSupported()) {
    new Notification({ title, body }).show();
  }
});
