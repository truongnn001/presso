/**
 * PressO Desktop - Electron Main Process
 * =======================================
 * 
 * RESPONSIBILITY:
 * - Application window management
 * - IPC channel setup (contextBridge)
 * - Kernel process lifecycle
 * - Message routing: Renderer ↔ Main ↔ Kernel
 * 
 * ARCHITECTURAL ROLE:
 * - Bridge between Renderer (UI) and Kernel (Java)
 * - Spawns Java kernel as child process
 * - Communicates via stdin/stdout (JSON messages)
 * - NO business logic here
 * 
 * Reference: PROJECT_DOCUMENTATION.md Section 4.1, 3.3
 */

const { app, BrowserWindow, ipcMain } = require('electron');
const { spawn } = require('child_process');
const path = require('path');
const readline = require('readline');

// Keep a global reference to prevent garbage collection
let mainWindow = null;

// Kernel process handle
let kernelProcess = null;
let kernelReady = false;

// Pending requests waiting for kernel response
const pendingRequests = new Map();

// Kernel output line reader
let kernelReader = null;

// ============================================================================
// KERNEL PROCESS MANAGEMENT
// ============================================================================

/**
 * Get the path to the kernel JAR file.
 * @returns {string} Path to presso-kernel.jar
 */
function getKernelJarPath() {
  // In development, look in the presso-kernel build directory
  // In production, it would be in the app resources
  const devPath = path.join(__dirname, '..', 'presso-kernel', 'build', 'libs', 'presso-kernel-0.1.0.jar');
  const prodPath = path.join(process.resourcesPath, 'kernel', 'presso-kernel.jar');
  
  // Check if dev path exists (we're in development)
  const fs = require('fs');
  if (fs.existsSync(devPath)) {
    return devPath;
  }
  
  return prodPath;
}

/**
 * Spawn the Java kernel process.
 */
function spawnKernel() {
  const jarPath = getKernelJarPath();
  
  console.log('[Main] Spawning kernel:', jarPath);
  
  try {
    // Spawn Java process
    kernelProcess = spawn('java', ['-jar', jarPath], {
      stdio: ['pipe', 'pipe', 'pipe'],
      cwd: path.dirname(jarPath)
    });
    
    // Set up line reader for stdout (kernel responses)
    kernelReader = readline.createInterface({
      input: kernelProcess.stdout,
      crlfDelay: Infinity
    });
    
    // Handle each line from kernel stdout
    kernelReader.on('line', (line) => {
      handleKernelOutput(line);
    });
    
    // Handle kernel stderr (logging)
    kernelProcess.stderr.on('data', (data) => {
      console.log('[Kernel]', data.toString().trim());
    });
    
    // Handle kernel process exit
    kernelProcess.on('close', (code) => {
      console.log('[Main] Kernel process exited with code:', code);
      kernelReady = false;
      kernelProcess = null;
      
      // Notify renderer of kernel disconnect
      if (mainWindow && !mainWindow.isDestroyed()) {
        mainWindow.webContents.send('kernel:event', {
          type: 'disconnected',
          code: code
        });
      }
    });
    
    // Handle spawn errors
    kernelProcess.on('error', (err) => {
      console.error('[Main] Failed to spawn kernel:', err.message);
      kernelReady = false;
    });
    
    console.log('[Main] Kernel process spawned, PID:', kernelProcess.pid);
    
  } catch (error) {
    console.error('[Main] Error spawning kernel:', error.message);
    kernelProcess = null;
  }
}

/**
 * Handle output from the kernel process.
 * @param {string} line - A line of JSON output from kernel
 */
function handleKernelOutput(line) {
  if (!line || line.trim() === '') {
    return;
  }
  
  console.log('[Main] Kernel output:', line);
  
  try {
    const response = JSON.parse(line);
    
    // Check if this is the KERNEL_READY signal
    if (response.id === 'KERNEL_READY') {
      kernelReady = true;
      console.log('[Main] Kernel is ready');
      
      // Notify renderer
      if (mainWindow && !mainWindow.isDestroyed()) {
        mainWindow.webContents.send('kernel:event', {
          type: 'ready',
          info: response.result
        });
      }
      return;
    }
    
    // Look up pending request
    const requestId = response.id;
    if (pendingRequests.has(requestId)) {
      const { resolve } = pendingRequests.get(requestId);
      pendingRequests.delete(requestId);
      resolve(response);
    } else {
      // Unsolicited message - could be an event
      console.log('[Main] Unsolicited kernel message:', response);
      if (mainWindow && !mainWindow.isDestroyed()) {
        mainWindow.webContents.send('kernel:event', response);
      }
    }
    
  } catch (error) {
    console.error('[Main] Failed to parse kernel output:', error.message);
  }
}

/**
 * Send a message to the kernel and wait for response.
 * @param {object} message - The message to send
 * @returns {Promise<object>} The kernel response
 */
function sendToKernel(message) {
  return new Promise((resolve, reject) => {
    if (!kernelProcess || !kernelReady) {
      resolve({
        id: message.id,
        success: false,
        error: {
          code: 'KERNEL_NOT_READY',
          message: 'Kernel process is not running'
        },
        timestamp: Date.now()
      });
      return;
    }
    
    // Store pending request
    const timeout = setTimeout(() => {
      pendingRequests.delete(message.id);
      resolve({
        id: message.id,
        success: false,
        error: {
          code: 'TIMEOUT',
          message: 'Kernel did not respond within timeout'
        },
        timestamp: Date.now()
      });
    }, 30000); // 30 second timeout
    
    pendingRequests.set(message.id, { 
      resolve: (response) => {
        clearTimeout(timeout);
        resolve(response);
      },
      reject,
      timeout
    });
    
    // Send message to kernel via stdin
    const json = JSON.stringify(message);
    console.log('[Main] Sending to kernel:', json);
    kernelProcess.stdin.write(json + '\n');
  });
}

/**
 * Shutdown the kernel gracefully.
 */
async function shutdownKernel() {
  if (!kernelProcess) {
    return;
  }
  
  console.log('[Main] Shutting down kernel...');
  
  try {
    // Send shutdown command
    if (kernelReady) {
      const shutdownMsg = {
        id: 'shutdown_' + Date.now(),
        type: 'SHUTDOWN',
        payload: {},
        timestamp: Date.now()
      };
      
      kernelProcess.stdin.write(JSON.stringify(shutdownMsg) + '\n');
      
      // Wait briefly for graceful shutdown
      await new Promise(resolve => setTimeout(resolve, 2000));
    }
    
    // Force kill if still running
    if (kernelProcess && !kernelProcess.killed) {
      kernelProcess.kill('SIGTERM');
    }
    
  } catch (error) {
    console.error('[Main] Error during kernel shutdown:', error.message);
    if (kernelProcess) {
      kernelProcess.kill('SIGKILL');
    }
  }
  
  kernelProcess = null;
  kernelReady = false;
}

// ============================================================================
// WINDOW MANAGEMENT
// ============================================================================

/**
 * Create the main application window.
 */
function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 800,
    minWidth: 1024,
    minHeight: 600,
    backgroundColor: '#0d0d0d',
    title: 'PressO Desktop',
    icon: path.join(__dirname, 'assets', 'icon.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    },
    frame: true,
    show: false
  });

  mainWindow.loadFile(path.join(__dirname, 'renderer', 'index.html'));

  mainWindow.once('ready-to-show', () => {
    mainWindow.show();
    
    if (process.argv.includes('--dev')) {
      mainWindow.webContents.openDevTools();
    }
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
  });

  console.log('[Main] Window created');
}

// ============================================================================
// APPLICATION LIFECYCLE
// ============================================================================

app.whenReady().then(() => {
  createWindow();
  
  // Spawn kernel after window is ready
  spawnKernel();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });

  console.log('[Main] PressO Desktop started');
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

app.on('will-quit', async (event) => {
  // Prevent immediate quit to allow cleanup
  if (kernelProcess) {
    event.preventDefault();
    await shutdownKernel();
    app.quit();
  }
});

// ============================================================================
// IPC HANDLERS (Renderer ↔ Main ↔ Kernel)
// ============================================================================

/**
 * IPC: Get application info.
 */
ipcMain.handle('app:getInfo', async () => {
  return {
    name: 'PressO Desktop',
    version: app.getVersion(),
    electron: process.versions.electron,
    node: process.versions.node,
    platform: process.platform
  };
});

/**
 * IPC: Send command to kernel.
 * Routes message from renderer to Java kernel via stdin/stdout.
 */
ipcMain.handle('kernel:send', async (event, message) => {
  console.log('[Main] IPC kernel:send -', message.type);
  
  // Forward to kernel
  const response = await sendToKernel(message);
  
  console.log('[Main] Kernel response:', response.success ? 'success' : 'error');
  return response;
});

/**
 * IPC: Check kernel status.
 */
ipcMain.handle('kernel:status', async () => {
  return {
    running: kernelProcess !== null && !kernelProcess.killed,
    ready: kernelReady,
    pid: kernelProcess ? kernelProcess.pid : null,
    status: kernelReady ? 'RUNNING' : (kernelProcess ? 'STARTING' : 'STOPPED')
  };
});

/**
 * IPC: Restart kernel.
 */
ipcMain.handle('kernel:restart', async () => {
  console.log('[Main] Restarting kernel...');
  await shutdownKernel();
  spawnKernel();
  return { success: true };
});

/**
 * IPC: Navigation change notification.
 */
ipcMain.on('nav:change', (event, viewName) => {
  console.log('[Main] Navigation:', viewName);
});

console.log('[Main] IPC handlers registered');
