# PressO Desktop — Project Documentation

**Version:** 1.0  
**Status:** Architectural Specification  
**Last Updated:** December 27, 2025

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Problem Statement & Evolution from Legacy System](#2-problem-statement--evolution-from-legacy-system)
3. [High-Level Architecture Overview](#3-high-level-architecture-overview)
4. [Module Responsibilities & Boundaries](#4-module-responsibilities--boundaries)
5. [Data Storage Strategy (Local-first)](#5-data-storage-strategy-local-first)
6. [Security Architecture & Threat Boundaries](#6-security-architecture--threat-boundaries)
7. [Execution Flow (UI → Core → Engines)](#7-execution-flow-ui--core--engines)
8. [Deployment Model (Windows Desktop)](#8-deployment-model-windows-desktop)
9. [Development & Rollout Phases](#9-development--rollout-phases)
10. [Future Expansion Considerations](#10-future-expansion-considerations)

---

## 1. Project Overview

### 1.1 Purpose

PressO Desktop is a **locally-installed work automation command hub** designed to centralize document processing, contract management, marketing workflows, and AI-assisted operations within a unified desktop application. The system operates as a long-running background service with a graphical user interface, emphasizing offline-first operation and local data ownership.

### 1.2 Core Identity

- **Central Command Hub:** Not a utility tool, but an orchestration platform for business automation workflows.
- **Offline-First:** Full functionality without internet connectivity; external services are optional enhancements.
- **Local Data Ownership:** User data remains on the local machine unless explicitly configured otherwise.
- **Security-First:** Clear isolation between UI, orchestration, and processing layers with explicit trust boundaries.

### 1.3 Initial Target Platform

- **Primary Platform:** Windows 7 and above (32-bit and 64-bit)
- **Architecture:** Desktop-installed application with system tray presence
- **Runtime Model:** Long-running background process with on-demand UI activation

### 1.4 Key Functional Domains

| Domain | Description |
|--------|-------------|
| **Contract Management** | Contract pricing calculation, VAT computation, multi-format export (Excel, PDF, Image) |
| **Document Processing** | PDF manipulation (merge, split, rotate, watermark), image conversion and compression |
| **AI Services** | Document learning, question-answering, content generation via local or remote LLM integration |
| **Marketing Automation** | Campaign management, content scheduling, budget tracking |
| **External Integration** | Third-party API connectivity for CRM, e-commerce, and communication platforms |

---

## 2. Problem Statement & Evolution from Legacy System

### 2.1 Legacy System Characteristics

The original PressO system was implemented as a web-based application with the following architecture:

| Component | Technology | Role |
|-----------|------------|------|
| Frontend | Next.js 16, React 18, TypeScript | Browser-based UI |
| Backend | Express.js, Node.js | API server, document generation |
| Processing | Puppeteer, xlsx-populate, pdf-lib | Server-side rendering and file manipulation |
| Storage | Browser LocalStorage | Session preferences only |

**Legacy Limitations:**

1. **Browser Dependency:** Required web browser for operation, introducing latency and memory overhead.
2. **Server Requirement:** Backend services needed to run continuously, consuming system resources even when idle.
3. **Limited Offline Capability:** Core functions required both frontend and backend servers running simultaneously.
4. **Data Volatility:** Heavy reliance on browser storage with no structured persistence layer.
5. **Deployment Complexity:** Two separate processes (frontend on port 8800, backend on port 8808) required coordination.
6. **Security Surface:** HTTP-based communication between frontend and backend exposed attack vectors.

### 2.2 Motivation for Transformation

The transformation to a desktop application addresses these limitations by:

1. **Unified Runtime:** Single application process replacing separate frontend/backend servers.
2. **Native Performance:** Direct system access without browser abstraction layer.
3. **Persistent Storage:** SQLite database for structured data with proper transaction support.
4. **Reduced Attack Surface:** Internal IPC communication replacing HTTP-based API calls.
5. **Simplified Deployment:** Single installer package with no external dependencies visible to users.
6. **Offline Operation:** Complete functionality without network connectivity for core workflows.

### 2.3 Preserved Concepts

The following architectural concepts from the legacy system inform the new design:

- **Modular Feature Organization:** Sidebar navigation with distinct functional areas
- **Document Generation Pipeline:** Template-based Excel generation, HTML-to-image/PDF rendering
- **VAT Calculation Engine:** Bidirectional calculation (before/after VAT) with configurable rates
- **Currency Formatting:** Vietnamese locale formatting (dot thousand separator, comma decimal)
- **Dark Theme UI:** Professional dark-mode interface with accent color system
- **Security Headers:** Content Security Policy, XSS protection, rate limiting concepts

---

## 3. High-Level Architecture Overview

### 3.1 Architectural Paradigm

PressO Desktop follows a **polyglot microkernel architecture** where a central orchestration kernel coordinates specialized processing engines:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              USER INTERFACE LAYER                            │
│                         (Electron + JavaScript/TypeScript)                   │
│                                                                              │
│   ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐       │
│   │  Dashboard  │  │  Contracts  │  │  Documents  │  │  Settings   │       │
│   └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘       │
└──────────────────────────────┬──────────────────────────────────────────────┘
                               │ IPC (contextBridge)
                               ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          ORCHESTRATION KERNEL                                │
│                                  (Java)                                      │
│                                                                              │
│   ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐         │
│   │  Task Scheduler  │  │  Module Router   │  │ Lifecycle Manager│         │
│   └──────────────────┘  └──────────────────┘  └──────────────────┘         │
│                                                                              │
│   ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐         │
│   │  State Manager   │  │  Event Bus       │  │ Security Gateway │         │
│   └──────────────────┘  └──────────────────┘  └──────────────────┘         │
└────┬─────────────────────────┬─────────────────────────┬────────────────────┘
     │                         │                         │
     ▼                         ▼                         ▼
┌─────────────┐         ┌─────────────┐          ┌─────────────┐
│   PYTHON    │         │    RUST     │          │     GO      │
│   ENGINE    │         │   ENGINE    │          │   API HUB   │
│             │         │             │          │             │
│ • AI/LLM    │         │ • Crypto    │          │ • External  │
│ • OCR       │         │ • Perf-Crit │          │   APIs      │
│ • PDF Proc  │         │ • Parallel  │          │ • Auth      │
│ • Excel Gen │         │   Compute   │          │ • Normalize │
└─────────────┘         └─────────────┘          └─────────────┘
       │                       │                        │
       └───────────────────────┴────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            LOCAL STORAGE LAYER                               │
│                                                                              │
│        ┌──────────────────────────┐    ┌──────────────────────────┐        │
│        │       SQLite DB          │    │      JSON Files          │        │
│        │                          │    │                          │        │
│        │ • Execution History      │    │ • User Settings          │        │
│        │ • Activity Logs          │    │ • UI Preferences         │        │
│        │ • Metadata Cache         │    │ • Module Config          │        │
│        │ • User Data Records      │    │ • Template Definitions   │        │
│        └──────────────────────────┘    └──────────────────────────┘        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.2 Language Responsibilities

| Language | Component | Responsibility |
|----------|-----------|----------------|
| **Java** | Orchestration Kernel | Global coordination, task scheduling, inter-module messaging, system lifecycle |
| **Python** | Document/AI Engine | PDF/Image processing, OCR, Excel generation, AI/LLM integration, export services |
| **Rust** | Performance Engine | CPU-intensive operations, cryptographic functions, parallel computation |
| **Go** | API Hub | External API gateway, authentication handling, request normalization, rate control |
| **TypeScript** | UI Layer | Desktop interface via Electron, user interaction handling |

### 3.3 Communication Patterns

**UI ↔ Kernel:**
- Electron IPC via contextBridge (secure channel)
- Message-based request/response pattern
- Unidirectional data flow for state updates

**Kernel ↔ Engines:**
- Standard I/O pipes for subprocess communication
- JSON-RPC 2.0 protocol for structured messaging
- Named pipes (Windows) or Unix sockets (future platforms) for high-throughput channels

**Engine ↔ Storage:**
- SQLite accessed via Kernel mediation (not direct from engines)
- File system access through Kernel-controlled paths only

---

## 4. Module Responsibilities & Boundaries

### 4.1 User Interface Layer (Electron/TypeScript)

**Scope:**
- Render application windows and UI components
- Capture user input and gestures
- Display data and feedback to users
- Manage UI state (component-level only)

**Boundaries:**
- NO direct file system access beyond user-initiated file dialogs
- NO direct network access
- NO business logic execution
- NO direct database access

**Interaction Model:**
- Send commands to Kernel via IPC
- Receive state updates and events from Kernel
- Request file operations through Kernel-controlled APIs

### 4.2 Orchestration Kernel (Java)

**Scope:**
- Application lifecycle management (startup, shutdown, recovery)
- Task queue management and scheduling
- Inter-module routing and coordination
- Global state management
- Security policy enforcement
- Database access coordination
- Engine process management (spawn, monitor, restart)

**Boundaries:**
- NO direct user interface rendering
- NO direct document processing (delegates to engines)
- NO external network calls (delegates to API Hub)

**Core Components:**

| Component | Responsibility |
|-----------|----------------|
| `TaskScheduler` | Queue management, priority handling, concurrent execution control |
| `ModuleRouter` | Route requests to appropriate engine based on operation type |
| `LifecycleManager` | Engine process supervision, health checks, graceful shutdown |
| `StateManager` | Application state persistence, configuration loading |
| `EventBus` | Internal publish-subscribe messaging for decoupled components |
| `SecurityGateway` | Access control validation, sensitive data handling policies |

### 4.3 Python Engine

**Scope:**
- Document processing (PDF, Image, Excel)
- OCR and text extraction
- AI/LLM integration (local models or API proxying via Go Hub)
- Template rendering and export generation
- File format conversion

**Key Capabilities:**

| Capability | Description |
|------------|-------------|
| **Excel Generation** | Template-based workbook creation using openpyxl or xlsxwriter |
| **PDF Processing** | Merge, split, rotate, watermark using PyPDF2 or pikepdf |
| **Image Processing** | Compression, format conversion, resize using Pillow |
| **OCR** | Text extraction from images using Tesseract bindings |
| **AI Integration** | LLM inference via local models (llama.cpp) or remote API calls |

**Boundaries:**
- NO direct database writes (returns results to Kernel)
- NO direct UI interaction
- NO external API calls (routes through Go API Hub)
- Receives work items via stdin, returns results via stdout

### 4.4 Rust Engine

**Scope:**
- CPU-intensive computations requiring maximum performance
- Cryptographic operations
- Parallel processing tasks
- Performance-critical algorithms

**Key Capabilities:**

| Capability | Description |
|------------|-------------|
| **Parallel Processing** | Multi-threaded batch operations using rayon |
| **Cryptography** | Encryption, hashing, digital signatures for sensitive data |
| **Data Compression** | High-performance compression/decompression |
| **Complex Calculations** | Financial computations, statistical analysis |

**Boundaries:**
- Stateless operation (no persistent state)
- NO file system access beyond provided input paths
- NO network access
- Receives work via stdin JSON, returns results via stdout

### 4.5 Go API Hub

**Scope:**
- All external network communication
- Third-party API integration
- Authentication and credential management
- Request/response normalization
- Rate limiting and retry logic

**Key Capabilities:**

| Capability | Description |
|------------|-------------|
| **API Gateway** | Unified interface for all external service calls |
| **Auth Management** | OAuth flows, API key handling, token refresh |
| **Rate Control** | Per-service rate limiting to prevent API abuse |
| **Response Normalization** | Convert varied API responses to internal format |
| **Connection Pooling** | Efficient HTTP client management |

**Boundaries:**
- ONLY component allowed to make external network requests
- NO database access
- NO direct UI interaction
- Credentials stored encrypted, accessed via Kernel

---

## 5. Data Storage Strategy (Local-first)

### 5.1 Storage Architecture

```
%APPDATA%\PressO\
├── data\
│   ├── presso.db              # SQLite main database
│   ├── presso.db-wal          # Write-ahead log
│   └── presso.db-shm          # Shared memory
├── config\
│   ├── settings.json          # User preferences
│   ├── modules.json           # Module configuration
│   └── ui-state.json          # UI layout persistence
├── templates\
│   ├── excel\                 # Excel template files
│   ├── pdf\                   # PDF templates
│   └── reports\               # Report templates
├── cache\
│   ├── thumbnails\            # Image preview cache
│   └── temp\                  # Temporary processing files
├── logs\
│   └── app.log                # Application logs (rotated)
└── secure\
    └── credentials.enc        # Encrypted API credentials
```

### 5.2 SQLite Database Schema (Conceptual)

**Core Tables:**

```sql
-- Execution history for all operations
CREATE TABLE execution_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    operation_type TEXT NOT NULL,
    module TEXT NOT NULL,
    started_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME,
    status TEXT CHECK(status IN ('pending', 'running', 'completed', 'failed')),
    input_summary TEXT,
    output_summary TEXT,
    error_message TEXT
);

-- Contract records
CREATE TABLE contracts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    contract_number TEXT UNIQUE,
    name TEXT NOT NULL,
    signed_date DATE,
    buyer_company TEXT,
    buyer_tax_code TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Payment stages linked to contracts
CREATE TABLE payment_stages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    contract_id INTEGER REFERENCES contracts(id),
    stage_name TEXT NOT NULL,
    price_before_vat REAL,
    vat_rate REAL DEFAULT 0.10,
    vat_amount REAL,
    price_after_vat REAL,
    sequence_order INTEGER
);

-- Activity log for audit trail
CREATE TABLE activity_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    user_action TEXT NOT NULL,
    entity_type TEXT,
    entity_id INTEGER,
    details TEXT
);
```

### 5.3 JSON Configuration Files

**settings.json:**
```json
{
  "version": "1.0",
  "general": {
    "language": "vi-VN",
    "theme": "dark",
    "startMinimized": false,
    "launchOnStartup": false
  },
  "export": {
    "defaultPath": "",
    "imageQuality": 95,
    "pdfCompression": "medium"
  },
  "vat": {
    "defaultRate": 0.10,
    "calculationMode": "before-vat"
  }
}
```

**modules.json:**
```json
{
  "python": {
    "enabled": true,
    "path": "${APP}/engines/python/python.exe",
    "maxConcurrent": 2
  },
  "rust": {
    "enabled": true,
    "path": "${APP}/engines/rust/presso-rust.exe"
  },
  "go": {
    "enabled": true,
    "path": "${APP}/engines/go/api-hub.exe",
    "port": 0
  }
}
```

### 5.4 Data Ownership Principles

1. **No Remote Storage by Default:** All data remains on the local machine unless user explicitly enables synchronization.
2. **Export Over Sync:** Users export data manually rather than automatic cloud backup.
3. **Portable Data Format:** SQLite and JSON formats allow direct migration and backup.
4. **No Hidden Telemetry:** No data transmitted without explicit user consent and visibility.

---

## 6. Security Architecture & Threat Boundaries

### 6.1 Security Principles

| Principle | Implementation |
|-----------|----------------|
| **Least Privilege** | Each module operates with minimum required permissions |
| **Defense in Depth** | Multiple validation layers between UI, Kernel, and Engines |
| **Isolation** | Engine processes run in separate memory spaces |
| **Secure Defaults** | All security features enabled by default |
| **Fail Secure** | Errors result in denied access, not granted |

### 6.2 Trust Boundaries

```
┌─────────────────────────────────────────────────────────────────────────┐
│ UNTRUSTED ZONE                                                          │
│                                                                         │
│  ┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐  │
│  │ External APIs   │     │ User Input      │     │ Imported Files  │  │
│  └────────┬────────┘     └────────┬────────┘     └────────┬────────┘  │
│           │                       │                       │           │
└───────────┼───────────────────────┼───────────────────────┼───────────┘
            │                       │                       │
            ▼                       ▼                       ▼
┌───────────────────────────────────────────────────────────────────────┐
│ VALIDATION BOUNDARY                                                    │
│                                                                        │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐              │
│  │ Go API Hub   │   │ UI Input     │   │ File         │              │
│  │ Validation   │   │ Sanitization │   │ Validation   │              │
│  └──────────────┘   └──────────────┘   └──────────────┘              │
│                                                                        │
└────────────────────────────────┬───────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ TRUSTED ZONE (Internal Application)                                     │
│                                                                         │
│   ┌─────────────────────────────────────────────────────────────────┐  │
│   │ ORCHESTRATION KERNEL                                             │  │
│   │                                                                  │  │
│   │  • Access Control Enforcement                                    │  │
│   │  • Operation Authorization                                       │  │
│   │  • Audit Logging                                                 │  │
│   │  • Credential Management                                         │  │
│   │                                                                  │  │
│   └──────────────────────────────┬──────────────────────────────────┘  │
│                                  │                                      │
│      ┌───────────────────────────┼───────────────────────────┐         │
│      │                           │                           │         │
│      ▼                           ▼                           ▼         │
│  ┌─────────┐              ┌─────────┐              ┌─────────┐        │
│  │ Python  │              │  Rust   │              │  Data   │        │
│  │ Engine  │              │ Engine  │              │  Store  │        │
│  └─────────┘              └─────────┘              └─────────┘        │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 6.3 Threat Mitigation

| Threat Category | Mitigation |
|-----------------|------------|
| **Injection Attacks** | Parameterized queries, input sanitization at Kernel level |
| **Path Traversal** | Kernel validates all file paths against allowed directories |
| **Code Injection** | No dynamic code execution from external input |
| **Credential Theft** | Encrypted storage, memory-only decryption |
| **API Key Exposure** | Keys managed by Go Hub, never exposed to UI layer |
| **Malicious Documents** | File validation before processing, sandboxed parsing |
| **Supply Chain** | Dependency locking, signature verification for engines |

### 6.4 Sensitive Data Handling

**Credential Storage:**
- API keys and credentials stored in `secure/credentials.enc`
- Encrypted using Windows DPAPI (Data Protection API) on Windows
- Decrypted only in memory when needed by Go API Hub
- Never logged or transmitted in plain text

**Data Classification:**

| Classification | Examples | Handling |
|----------------|----------|----------|
| **Public** | UI preferences, theme settings | Plain JSON files |
| **Internal** | Execution history, activity logs | SQLite (unencrypted) |
| **Confidential** | Contract data, buyer information | SQLite with access control |
| **Secret** | API credentials, tokens | Encrypted file, memory-only decryption |

### 6.5 Network Security

- **No Implicit Connections:** Application makes no network connections without explicit user configuration
- **TLS Enforcement:** All external API calls via Go Hub use TLS 1.2+
- **Certificate Validation:** Standard certificate chain validation; no self-signed cert bypass
- **Proxy Support:** Respect system proxy settings when configured

---

## 7. Execution Flow (UI → Core → Engines)

### 7.1 Request Processing Lifecycle

```
User Action → UI Event → IPC Message → Kernel Routing → Engine Dispatch → Processing → Result Return → State Update → UI Update
```

### 7.2 Example Flow: Generate Contract Excel

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ 1. USER ACTION                                                                │
│    User clicks "Export Excel" button in Contract Pricing screen              │
└────────────────────────────────────┬─────────────────────────────────────────┘
                                     │
                                     ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ 2. UI LAYER (Electron Renderer)                                               │
│                                                                               │
│    • Collect form data (contract info, payment stages)                       │
│    • Validate required fields locally                                         │
│    • Construct request payload                                                │
│    • Send IPC message: { type: 'EXPORT_EXCEL', payload: {...} }              │
│                                                                               │
└────────────────────────────────────┬─────────────────────────────────────────┘
                                     │ IPC (contextBridge)
                                     ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ 3. KERNEL (Java)                                                              │
│                                                                               │
│    TaskScheduler:                                                             │
│    • Receive IPC message via Electron main process bridge                     │
│    • Create Task object with unique ID                                        │
│    • Add to processing queue                                                  │
│                                                                               │
│    ModuleRouter:                                                              │
│    • Identify operation type: EXPORT_EXCEL → Python Engine                    │
│    • Verify Python Engine is available                                        │
│                                                                               │
│    SecurityGateway:                                                           │
│    • Validate input data structure                                            │
│    • Sanitize file paths                                                      │
│    • Check export directory permissions                                       │
│                                                                               │
└────────────────────────────────────┬─────────────────────────────────────────┘
                                     │ JSON-RPC via stdin/stdout
                                     ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ 4. PYTHON ENGINE                                                              │
│                                                                               │
│    • Receive work item: { method: 'generate_excel', params: {...} }          │
│    • Load Excel template from templates/excel/                                │
│    • Populate cells with contract data                                        │
│    • Apply formatting (currency, dates, styles)                               │
│    • Calculate totals and VAT                                                 │
│    • Write output to temp location                                            │
│    • Return: { result: { path: '/temp/output.xlsx', success: true } }        │
│                                                                               │
└────────────────────────────────────┬─────────────────────────────────────────┘
                                     │
                                     ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ 5. KERNEL (Java) - Response Handling                                          │
│                                                                               │
│    • Receive result from Python Engine                                        │
│    • Log execution to activity_log table                                      │
│    • Move file from temp to user-specified location                           │
│    • Update task status to 'completed'                                        │
│    • Send IPC response to UI                                                  │
│                                                                               │
└────────────────────────────────────┬─────────────────────────────────────────┘
                                     │
                                     ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ 6. UI LAYER (Electron Renderer)                                               │
│                                                                               │
│    • Receive success response                                                 │
│    • Show success notification to user                                        │
│    • Optionally open file location or preview                                 │
│                                                                               │
└──────────────────────────────────────────────────────────────────────────────┘
```

### 7.3 Error Handling Flow

```
Engine Error → Kernel Catches → Log to DB → Retry Logic (if applicable) → Error Response to UI → User Notification
```

**Error Categories:**

| Category | Handling |
|----------|----------|
| **Recoverable** | Automatic retry with exponential backoff |
| **User-Fixable** | Return specific error message for UI display |
| **System Error** | Log details, show generic message, optionally report |
| **Engine Crash** | Kernel detects, restarts engine, retries queued tasks |

### 7.4 Concurrent Operation Model

- **Maximum Concurrent Tasks:** Configurable per engine (default: 2 for Python, 4 for Rust)
- **Queue Strategy:** FIFO with priority override for user-initiated actions
- **Cancellation:** Tasks can be cancelled; engines receive cancel signal via IPC

---

## 8. Deployment Model (Windows Desktop)

### 8.1 Installation Package Structure

```
PressO-Setup.exe (NSIS or WiX installer)
│
├── Core Application
│   ├── PressO.exe              # Electron main executable
│   ├── resources/
│   │   └── app.asar            # Bundled UI application
│   └── presso-kernel.jar       # Java orchestration kernel
│
├── Engines
│   ├── python/
│   │   ├── python.exe          # Embedded Python runtime
│   │   ├── Lib/                # Standard library
│   │   └── site-packages/      # Required packages (pre-installed)
│   │       ├── openpyxl/
│   │       ├── Pillow/
│   │       ├── pypdf2/
│   │       └── ...
│   │
│   ├── rust/
│   │   └── presso-rust.exe     # Compiled Rust binary
│   │
│   └── go/
│       └── api-hub.exe         # Compiled Go binary
│
├── Java Runtime
│   └── jre/                    # Embedded JRE (minimal)
│
├── Templates
│   └── templates/              # Excel, PDF templates
│
└── Resources
    └── locales/                # Internationalization files
```

### 8.2 System Requirements

| Requirement | Minimum | Recommended |
|-------------|---------|-------------|
| **OS** | Windows 7 SP1 | Windows 10/11 |
| **Architecture** | x64 | x64 |
| **RAM** | 4 GB | 8 GB+ |
| **Disk Space** | 500 MB | 1 GB |
| **Display** | 1280×720 | 1920×1080 |

### 8.3 Installation Process

1. **Installer Execution:** User runs setup executable
2. **Dependency Check:** Verify no conflicting installations
3. **File Extraction:** Copy files to `%PROGRAMFILES%\PressO\`
4. **User Data Directory:** Create `%APPDATA%\PressO\` structure
5. **Shortcut Creation:** Desktop and Start Menu shortcuts
6. **Registry Entries:** Uninstall information, file associations (optional)
7. **First Run Setup:** Initial configuration wizard on first launch

### 8.4 Update Mechanism

- **Manual Updates:** User downloads new installer (Phase 1)
- **Future: Auto-Update:** Delta updates via separate update service (Phase 2+)
- **Data Preservation:** Updates preserve user data in `%APPDATA%`

### 8.5 Uninstallation

- Remove program files from `%PROGRAMFILES%`
- Optionally remove user data (with confirmation)
- Clean registry entries
- Remove shortcuts

---

## 9. Development & Rollout Phases

### Phase 1: Foundation (Core Infrastructure)

**Duration:** 8-12 weeks

**Objectives:**
- Establish project structure and build system
- Implement Java orchestration kernel with basic lifecycle management
- Create Electron UI shell with navigation
- Implement IPC communication layer
- Set up SQLite database initialization
- Basic settings management (JSON)

**Deliverables:**
- Running application skeleton
- Module loading framework
- Basic logging infrastructure
- Development environment documentation

**Success Criteria:**
- Application launches and displays UI
- Kernel successfully manages engine lifecycle
- Basic IPC round-trip communication works

---

### Phase 2: Document Processing (Python Engine)

**Duration:** 8-10 weeks

**Objectives:**
- Implement Python engine subprocess management
- Port Excel generation from legacy system
- Port PDF processing capabilities
- Port image processing capabilities
- Implement template management

**Deliverables:**
- Contract pricing screen (full port from legacy)
- Excel export functionality
- PDF export functionality (via HTML rendering)
- Image export functionality
- PDF manipulation tools (merge, split, rotate)

**Success Criteria:**
- Excel generation produces identical output to legacy system
- PDF processing handles all legacy use cases
- Image compression and conversion functional

---

### Phase 3: Data Persistence & History

**Duration:** 4-6 weeks

**Objectives:**
- Implement execution history tracking
- Build activity logging system
- Create contract data persistence
- Implement search and retrieval

**Deliverables:**
- Execution history viewer
- Activity audit log
- Saved contracts management
- Data export/import capability

**Success Criteria:**
- All operations logged to database
- Historical data queryable and viewable
- Data survives application restart

---

### Phase 4: External Integration (Go API Hub)

**Duration:** 6-8 weeks

**Objectives:**
- Implement Go API Hub engine
- Build credential management system
- Implement external API integrations
- Create integration configuration UI

**Deliverables:**
- API Hub running as managed subprocess
- Secure credential storage
- Sample integrations (tax code lookup, etc.)
- Integration settings panel

**Success Criteria:**
- External API calls route through Hub only
- Credentials encrypted at rest
- Rate limiting functional

---

### Phase 5: Performance & Polish

**Duration:** 4-6 weeks

**Objectives:**
- Implement Rust engine for performance-critical operations
- Optimize startup time
- Refine UI/UX based on testing
- Comprehensive error handling
- Documentation completion

**Deliverables:**
- Rust engine integration
- Performance benchmarks
- User documentation
- Installation package

**Success Criteria:**
- Application starts in <5 seconds
- All error cases handled gracefully
- Installer tested on Windows 7, 10, 11

---

### Phase 6: AI Integration

**Duration:** 6-8 weeks (parallel or sequential based on resources)

**Objectives:**
- Integrate local LLM capability (optional, resource-dependent)
- Implement document learning features
- Build Q&A interface
- Connect to remote AI APIs via Go Hub

**Deliverables:**
- AI Documents feature (port from legacy concept)
- Local inference option
- Remote API integration option
- AI settings and model management

**Success Criteria:**
- AI features functional offline (local model)
- Remote AI features work via API Hub
- Reasonable response times for document Q&A

---

## 10. Future Expansion Considerations

The following items are documented for awareness but represent no commitment for implementation. They may inform architectural decisions to avoid future rework.

### 10.1 Platform Expansion

- **macOS Support:** Electron provides cross-platform UI; kernel and engines may require platform-specific builds. Java kernel portable; Python embedded runtime available for macOS; Go and Rust compile natively.
- **Linux Support:** Similar considerations as macOS. Likely Debian/Ubuntu focus initially.

### 10.2 Optional Cloud Features

- **Sync Service:** If users request, optional encrypted cloud backup could be offered. Would require user account system and server infrastructure.
- **Team Features:** Multi-user scenarios with shared data would require significant architecture changes.

### 10.3 Plugin Architecture

- **Third-Party Extensions:** The modular engine design could extend to support user-installed plugins. Would require security review and sandboxing.
- **Custom Templates:** User-created templates for Excel/PDF generation.

### 10.4 Mobile Companion

- **Mobile Viewer:** Read-only mobile app for viewing generated documents. Would require export format or sync mechanism.

### 10.5 Advanced AI

- **Custom Model Training:** Fine-tuning on user documents for improved Q&A accuracy.
- **Automation Workflows:** AI-suggested or AI-generated workflow automation.

---

## Appendix A: Glossary

| Term | Definition |
|------|------------|
| **Kernel** | The Java-based central orchestration component managing all modules |
| **Engine** | A specialized processing component (Python, Rust, Go) handling specific operations |
| **IPC** | Inter-Process Communication; messaging between Electron and Kernel |
| **API Hub** | The Go-based component handling all external network communication |
| **Offline-First** | Design principle where core features work without network connectivity |
| **Local-First** | Design principle where user data remains on local machine by default |

## Appendix B: Reference Documents

- Legacy PressO Documentation (NodeJS/NextJS version)
- OWASP Security Guidelines
- Electron Security Best Practices
- SQLite Documentation

---

**Document Control**

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-12-27 | Architecture Team | Initial specification |

---

*This document serves as the authoritative architectural reference for PressO Desktop development. All implementation decisions should align with the principles and boundaries defined herein.*

