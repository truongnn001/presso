/**
 * PressO Desktop - Renderer Application
 * ======================================
 * 
 * RESPONSIBILITY:
 * - UI state management (view switching)
 * - IPC communication via window.presso
 * - User interaction handling
 * - IPC round-trip testing (PING/PONG)
 * 
 * BOUNDARIES (per PROJECT_DOCUMENTATION.md Section 4.1):
 * - NO direct file system access
 * - NO business logic execution
 * - NO direct network access
 * - All operations go through IPC to Kernel
 */

(function() {
  'use strict';

  // ========================================
  // State
  // ========================================
  
  const state = {
    currentView: 'dashboard',
    kernelConnected: false,
    kernelPid: null,
    messageLog: []
  };

  // ========================================
  // DOM References
  // ========================================
  
  const elements = {
    navItems: document.querySelectorAll('.nav-item'),
    views: document.querySelectorAll('.view'),
    kernelStatus: document.getElementById('kernel-status'),
    kernelPid: document.getElementById('kernel-pid'),
    toastContainer: document.getElementById('toast-container'),
    appVersion: document.getElementById('app-version'),
    // IPC Test elements
    btnPing: document.getElementById('btn-ping'),
    btnPythonPing: document.getElementById('btn-python-ping'),
    btnEngineStatus: document.getElementById('btn-engine-status'),
    btnRestartKernel: document.getElementById('btn-restart-kernel'),
    btnClearLog: document.getElementById('btn-clear-log'),
    ipcResult: document.getElementById('ipc-result-content'),
    ipcLogContent: document.getElementById('ipc-log-content'),
    // System info
    infoPlatform: document.getElementById('info-platform'),
    infoElectron: document.getElementById('info-electron'),
    infoNode: document.getElementById('info-node'),
    infoKernel: document.getElementById('info-kernel')
  };

  // ========================================
  // Navigation
  // ========================================
  
  /**
   * Switch to a different view.
   * @param {string} viewName - The view to switch to
   */
  function navigateTo(viewName) {
    elements.navItems.forEach(item => {
      item.classList.toggle('active', item.dataset.view === viewName);
    });
    
    elements.views.forEach(view => {
      const isTarget = view.id === `view-${viewName}`;
      view.classList.toggle('active', isTarget);
    });
    
    state.currentView = viewName;
    
    if (window.presso) {
      window.presso.notifyNavigation(viewName);
    }
    
    console.log('[App] Navigated to:', viewName);
  }

  function initNavigation() {
    elements.navItems.forEach(item => {
      item.addEventListener('click', () => {
        navigateTo(item.dataset.view);
      });
    });
  }

  // ========================================
  // Toast Notifications
  // ========================================
  
  function showToast(message, type = 'info', duration = 3000) {
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.innerHTML = `<span class="toast-message">${message}</span>`;
    
    elements.toastContainer.appendChild(toast);
    
    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transform = 'translateX(100%)';
      setTimeout(() => toast.remove(), 300);
    }, duration);
  }

  // ========================================
  // IPC Logging
  // ========================================
  
  /**
   * Add an entry to the IPC message log.
   * @param {string} direction - 'outgoing' or 'incoming'
   * @param {string} message - The message summary
   */
  function addLogEntry(direction, message) {
    const time = new Date().toLocaleTimeString('en-US', { 
      hour12: false, 
      hour: '2-digit', 
      minute: '2-digit', 
      second: '2-digit',
      fractionalSecondDigits: 3
    });
    
    const arrow = direction === 'outgoing' ? '→' : '←';
    
    const entry = document.createElement('div');
    entry.className = 'log-entry';
    entry.innerHTML = `
      <span class="log-time">${time}</span>
      <span class="log-direction ${direction}">${arrow}</span>
      <span class="log-message">${escapeHtml(message)}</span>
    `;
    
    elements.ipcLogContent.appendChild(entry);
    elements.ipcLogContent.scrollTop = elements.ipcLogContent.scrollHeight;
    
    // Keep only last 50 entries
    while (elements.ipcLogContent.children.length > 50) {
      elements.ipcLogContent.removeChild(elements.ipcLogContent.firstChild);
    }
  }
  
  /**
   * Clear the IPC log.
   */
  function clearLog() {
    elements.ipcLogContent.innerHTML = '';
    addLogEntry('incoming', 'Log cleared');
  }
  
  /**
   * Escape HTML for safe display.
   */
  function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
  }

  // ========================================
  // Kernel Status
  // ========================================
  
  /**
   * Update kernel status display.
   * @param {object} status - Kernel status object
   */
  function updateKernelStatus(status) {
    state.kernelConnected = status.ready;
    state.kernelPid = status.pid;
    
    const dot = elements.kernelStatus.querySelector('.status-dot');
    const text = elements.kernelStatus.querySelector('.status-text');
    
    if (status.ready) {
      dot.className = 'status-dot online';
      text.textContent = 'Kernel Online';
    } else if (status.running) {
      dot.className = 'status-dot';
      dot.style.background = 'var(--status-warning)';
      text.textContent = 'Kernel Starting...';
    } else {
      dot.className = 'status-dot offline';
      text.textContent = 'Kernel Offline';
    }
    
    // Show PID
    if (elements.kernelPid) {
      elements.kernelPid.textContent = status.pid ? `PID: ${status.pid}` : '';
    }
    
    // Update settings page
    if (elements.infoKernel) {
      elements.infoKernel.textContent = status.ready ? `Running (PID: ${status.pid})` : status.status;
    }
  }

  async function checkKernelStatus() {
    if (!window.presso) {
      updateKernelStatus({ running: false, ready: false, status: 'IPC Not Available' });
      return;
    }
    
    try {
      const status = await window.presso.getKernelStatus();
      updateKernelStatus(status);
    } catch (error) {
      updateKernelStatus({ running: false, ready: false, status: 'Error' });
      console.error('[App] Kernel status check failed:', error);
    }
  }

  // ========================================
  // IPC PING Test
  // ========================================
  
  /**
   * Send PING to kernel and display response.
   */
  async function sendPing() {
    if (!window.presso) {
      showToast('IPC bridge not available', 'error');
      return;
    }
    
    // Update UI
    elements.btnPing.disabled = true;
    elements.ipcResult.textContent = 'Sending PING...';
    elements.ipcResult.className = 'ipc-result-content';
    
    // Log outgoing message
    addLogEntry('outgoing', 'PING → Kernel');
    
    const startTime = performance.now();
    
    try {
      console.log('[App] Sending PING to kernel...');
      
      const response = await window.presso.ping();
      
      const elapsed = (performance.now() - startTime).toFixed(2);
      
      console.log('[App] PING response:', response);
      
      if (response.success) {
        // Success - show PONG
        const result = response.result;
        const formattedResult = JSON.stringify(result, null, 2);
        
        elements.ipcResult.textContent = formattedResult;
        elements.ipcResult.className = 'ipc-result-content success';
        
        addLogEntry('incoming', `PONG ← Kernel (${elapsed}ms)`);
        showToast(`PONG received in ${elapsed}ms!`, 'success');
        
      } else {
        // Error response
        const errorMsg = response.error ? `${response.error.code}: ${response.error.message}` : 'Unknown error';
        
        elements.ipcResult.textContent = errorMsg;
        elements.ipcResult.className = 'ipc-result-content error';
        
        addLogEntry('incoming', `ERROR: ${response.error?.code || 'UNKNOWN'}`);
        showToast(errorMsg, 'error');
      }
      
    } catch (error) {
      console.error('[App] PING failed:', error);
      
      elements.ipcResult.textContent = `Error: ${error.message}`;
      elements.ipcResult.className = 'ipc-result-content error';
      
      addLogEntry('incoming', `EXCEPTION: ${error.message}`);
      showToast('PING failed: ' + error.message, 'error');
    }
    
    elements.btnPing.disabled = false;
  }
  
  /**
   * Send PING to Python engine via kernel.
   */
  async function sendPythonPing() {
    if (!window.presso) {
      showToast('IPC bridge not available', 'error');
      return;
    }
    
    elements.btnPythonPing.disabled = true;
    elements.ipcResult.textContent = 'Sending PING to Python Engine...';
    elements.ipcResult.className = 'ipc-result-content';
    
    addLogEntry('outgoing', 'PYTHON_PING → Kernel → Python');
    
    const startTime = performance.now();
    
    try {
      console.log('[App] Sending PING to Python engine...');
      
      const response = await window.presso.pythonPing();
      
      const elapsed = (performance.now() - startTime).toFixed(2);
      
      console.log('[App] Python PING response:', response);
      
      if (response.success) {
        const result = response.result;
        const formattedResult = JSON.stringify(
          typeof result === 'string' ? JSON.parse(result) : result, 
          null, 2
        );
        
        elements.ipcResult.textContent = formattedResult;
        elements.ipcResult.className = 'ipc-result-content success';
        
        addLogEntry('incoming', `PONG ← Python Engine (${elapsed}ms)`);
        showToast(`Python PONG received in ${elapsed}ms!`, 'success');
        
      } else {
        const errorMsg = response.error 
          ? `${response.error.code}: ${response.error.message}` 
          : 'Unknown error';
        
        elements.ipcResult.textContent = errorMsg;
        elements.ipcResult.className = 'ipc-result-content error';
        
        addLogEntry('incoming', `ERROR: ${response.error?.code || 'UNKNOWN'}`);
        showToast(errorMsg, 'error');
      }
      
    } catch (error) {
      console.error('[App] Python PING failed:', error);
      
      elements.ipcResult.textContent = `Error: ${error.message}`;
      elements.ipcResult.className = 'ipc-result-content error';
      
      addLogEntry('incoming', `EXCEPTION: ${error.message}`);
      showToast('Python PING failed: ' + error.message, 'error');
    }
    
    elements.btnPythonPing.disabled = false;
  }
  
  /**
   * Get engine status from kernel.
   */
  async function getEngineStatus() {
    if (!window.presso) {
      showToast('IPC bridge not available', 'error');
      return;
    }
    
    elements.btnEngineStatus.disabled = true;
    elements.ipcResult.textContent = 'Getting engine status...';
    elements.ipcResult.className = 'ipc-result-content';
    
    addLogEntry('outgoing', 'GET_ENGINE_STATUS → Kernel');
    
    try {
      const response = await window.presso.getEngineStatus();
      
      console.log('[App] Engine status response:', response);
      
      if (response.success) {
        const result = response.result;
        const formattedResult = JSON.stringify(
          typeof result === 'string' ? JSON.parse(result) : result, 
          null, 2
        );
        
        elements.ipcResult.textContent = formattedResult;
        elements.ipcResult.className = 'ipc-result-content';
        
        addLogEntry('incoming', 'Engine status received');
        
      } else {
        const errorMsg = response.error 
          ? `${response.error.code}: ${response.error.message}` 
          : 'Unknown error';
        
        elements.ipcResult.textContent = errorMsg;
        elements.ipcResult.className = 'ipc-result-content error';
        
        addLogEntry('incoming', `ERROR: ${response.error?.code || 'UNKNOWN'}`);
      }
      
    } catch (error) {
      console.error('[App] Get engine status failed:', error);
      elements.ipcResult.textContent = `Error: ${error.message}`;
      elements.ipcResult.className = 'ipc-result-content error';
    }
    
    elements.btnEngineStatus.disabled = false;
  }

  /**
   * Restart the kernel process.
   */
  async function restartKernel() {
    if (!window.presso) {
      showToast('IPC bridge not available', 'error');
      return;
    }
    
    elements.btnRestartKernel.disabled = true;
    addLogEntry('outgoing', 'Restart kernel requested');
    showToast('Restarting kernel...', 'info');
    
    try {
      await window.presso.restartKernel();
      addLogEntry('incoming', 'Kernel restart initiated');
      
      // Wait a bit then check status
      setTimeout(checkKernelStatus, 2000);
      setTimeout(checkKernelStatus, 5000);
      
    } catch (error) {
      console.error('[App] Kernel restart failed:', error);
      addLogEntry('incoming', `Restart failed: ${error.message}`);
      showToast('Kernel restart failed', 'error');
    }
    
    elements.btnRestartKernel.disabled = false;
  }

  // ========================================
  // System Info
  // ========================================
  
  async function loadSystemInfo() {
    if (!window.presso) {
      console.warn('[App] presso API not available');
      return;
    }
    
    try {
      const info = await window.presso.getAppInfo();
      
      if (elements.appVersion) {
        elements.appVersion.textContent = `v${info.version}`;
      }
      if (elements.infoPlatform) {
        elements.infoPlatform.textContent = info.platform;
      }
      if (elements.infoElectron) {
        elements.infoElectron.textContent = info.electron;
      }
      if (elements.infoNode) {
        elements.infoNode.textContent = info.node;
      }
      
      console.log('[App] System info loaded:', info);
    } catch (error) {
      console.error('[App] Failed to load system info:', error);
    }
  }

  // ========================================
  // Kernel Events
  // ========================================
  
  function setupKernelEventListener() {
    if (!window.presso || !window.presso.onKernelEvent) {
      return;
    }
    
    window.presso.onKernelEvent((event) => {
      console.log('[App] Kernel event:', event);
      
      if (event.type === 'ready') {
        addLogEntry('incoming', 'Kernel ready');
        showToast('Kernel is ready', 'success');
        checkKernelStatus();
      } else if (event.type === 'disconnected') {
        addLogEntry('incoming', `Kernel disconnected (code: ${event.code})`);
        showToast('Kernel disconnected', 'error');
        checkKernelStatus();
      }
    });
  }

  // ========================================
  // Initialization
  // ========================================
  
  async function init() {
    console.log('[App] Initializing PressO Desktop UI...');
    
    // Initialize navigation
    initNavigation();
    
    // Bind event handlers
    if (elements.btnPing) {
      elements.btnPing.addEventListener('click', sendPing);
    }
    if (elements.btnPythonPing) {
      elements.btnPythonPing.addEventListener('click', sendPythonPing);
    }
    if (elements.btnEngineStatus) {
      elements.btnEngineStatus.addEventListener('click', getEngineStatus);
    }
    if (elements.btnRestartKernel) {
      elements.btnRestartKernel.addEventListener('click', restartKernel);
    }
    if (elements.btnClearLog) {
      elements.btnClearLog.addEventListener('click', clearLog);
    }
    
    // Load system info
    await loadSystemInfo();
    
    // Check kernel status
    await checkKernelStatus();
    
    // Set up periodic kernel status check
    setInterval(checkKernelStatus, 5000);
    
    // Subscribe to kernel events
    setupKernelEventListener();
    
    // Initial log entry
    addLogEntry('incoming', 'UI initialized, waiting for kernel...');
    
    console.log('[App] Initialization complete');
    
    if (window.presso && window.presso.isDev) {
      showToast('Development mode active', 'info', 2000);
    }
  }

  // Run initialization
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

})();
