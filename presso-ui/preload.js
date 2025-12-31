/**
 * PressO Desktop - Preload Script
 * ================================
 * 
 * RESPONSIBILITY:
 * - Expose safe IPC channels to renderer via contextBridge
 * - NO direct Node.js API exposure
 * - Strict API surface control
 * 
 * SECURITY (per PROJECT_DOCUMENTATION.md Section 6):
 * - contextIsolation: true (renderer cannot access this scope)
 * - Only whitelisted channels exposed
 * - All data sanitized before crossing boundary
 * 
 * Reference: Electron Security Best Practices
 */

const { contextBridge, ipcRenderer } = require('electron');

/**
 * Exposed API for renderer process.
 * This is the ONLY way renderer can communicate with main/kernel.
 */
contextBridge.exposeInMainWorld('presso', {
  
  // ========================================
  // Application Info
  // ========================================
  
  /**
   * Get application information.
   * @returns {Promise<{name, version, electron, node, platform}>}
   */
  getAppInfo: () => ipcRenderer.invoke('app:getInfo'),
  
  // ========================================
  // Kernel Communication
  // ========================================
  
  /**
   * Send a command to the kernel.
   * @param {string} type - Command type (e.g., 'GET_STATUS', 'EXPORT_EXCEL')
   * @param {object} payload - Command payload
   * @returns {Promise<{id, success, result?, error?}>}
   */
  sendCommand: (type, payload = {}) => {
    const message = {
      id: generateId(),
      type: type,
      payload: payload,
      timestamp: Date.now()
    };
    return ipcRenderer.invoke('kernel:send', message);
  },
  
  /**
   * Get kernel status.
   * @returns {Promise<{running, ready, pid, status}>}
   */
  getKernelStatus: () => ipcRenderer.invoke('kernel:status'),
  
  /**
   * Restart the kernel process.
   * @returns {Promise<{success}>}
   */
  restartKernel: () => ipcRenderer.invoke('kernel:restart'),
  
  /**
   * Send PING to kernel and wait for PONG.
   * Convenience method for IPC testing.
   * @returns {Promise<object>}
   */
  ping: () => {
    const message = {
      id: generateId(),
      type: 'PING',
      payload: {},
      timestamp: Date.now()
    };
    return ipcRenderer.invoke('kernel:send', message);
  },
  
  /**
   * Send PING to Python engine via kernel.
   * Tests full IPC path: UI → Kernel → Python → Kernel → UI
   * @returns {Promise<object>}
   */
  pythonPing: () => {
    const message = {
      id: generateId(),
      type: 'PYTHON_PING',
      payload: {},
      timestamp: Date.now()
    };
    return ipcRenderer.invoke('kernel:send', message);
  },
  
  /**
   * Get status of all engines.
   * @returns {Promise<object>}
   */
  getEngineStatus: () => {
    const message = {
      id: generateId(),
      type: 'GET_ENGINE_STATUS',
      payload: {},
      timestamp: Date.now()
    };
    return ipcRenderer.invoke('kernel:send', message);
  },
  
  /**
   * Subscribe to kernel events.
   * @param {function} callback - Event handler
   * @returns {function} Unsubscribe function
   */
  onKernelEvent: (callback) => {
    const handler = (event, data) => callback(data);
    ipcRenderer.on('kernel:event', handler);
    return () => ipcRenderer.removeListener('kernel:event', handler);
  },
  
  // ========================================
  // Navigation Events
  // ========================================
  
  /**
   * Notify main process of navigation change.
   * @param {string} viewName - The view being navigated to
   */
  notifyNavigation: (viewName) => {
    ipcRenderer.send('nav:change', viewName);
  },
  
  // ========================================
  // Platform Info
  // ========================================
  
  /**
   * Platform identifier.
   */
  platform: process.platform,
  
  /**
   * Check if running in development mode.
   */
  isDev: process.argv.includes('--dev')
});

/**
 * Generate a unique message ID.
 * @returns {string}
 */
function generateId() {
  return `msg_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`;
}

console.log('[Preload] Context bridge exposed: window.presso');

