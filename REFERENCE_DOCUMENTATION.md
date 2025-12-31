# Kho Tài Liệu Tham Khảo — Dự Án PressO Desktop

**Phiên bản:** 1.0  
**Cập nhật:** 27/12/2025  
**Nguồn:** `D:\Document`

---

## Mục Lục

1. [Tổng Quan Kho Tài Liệu](#1-tổng-quan-kho-tài-liệu)
2. [Phân Loại Theo Chủ Đề](#2-phân-loại-theo-chủ-đề)
   - [2.1 Cơ Sở Dữ Liệu (Database)](#21-cơ-sở-dữ-liệu-database)
   - [2.2 Frontend & Web Design](#22-frontend--web-design)
   - [2.3 Fullstack & Kiến Trúc Hệ Thống](#23-fullstack--kiến-trúc-hệ-thống)
   - [2.4 Bảo Mật Server & Mạng](#24-bảo-mật-server--mạng)
   - [2.5 Bảo Mật Mã Nguồn](#25-bảo-mật-mã-nguồn)
3. [Ma Trận Ứng Dụng Theo Module Dự Án](#3-ma-trận-ứng-dụng-theo-module-dự-án)
4. [Thứ Tự Đọc Khuyến Nghị](#4-thứ-tự-đọc-khuyến-nghị)
5. [Chi Tiết Từng Tài Liệu](#5-chi-tiết-từng-tài-liệu)

---

## 1. Tổng Quan Kho Tài Liệu

Kho tài liệu tham khảo bao gồm **35+ tài liệu** được phân loại thành **5 thư mục chính**:

| Thư mục | Số lượng | Mô tả |
|---------|----------|-------|
| `Database` | 7 | Lý thuyết & thực hành cơ sở dữ liệu, SQLite chuyên sâu |
| `Frontend` | 3 | Thiết kế web, HTML/CSS/JavaScript, kiến trúc ứng dụng web |
| `Fullstack` | 35+ | Kiến trúc hệ thống, AI/ML, hiệu năng, hệ phân tán |
| `Security-Server` | 8 | Bảo mật mạng, server, web security tổng quan |
| `Security-Source-Code` | 10 | Lập trình an toàn, OWASP, secure coding practices |

### Cấu Trúc Thư Mục

```
D:\Document\
├── Database\                    ← Cơ sở dữ liệu & SQLite
├── Frontend\                    ← Thiết kế giao diện web
├── Fullstack\                   ← Kiến trúc, AI, hiệu năng
├── Security-Server\             ← Bảo mật server & mạng
└── Security-Source-Code\        ← Lập trình an toàn
```

---

## 2. Phân Loại Theo Chủ Đề

### 2.1 Cơ Sở Dữ Liệu (Database)

**Đường dẫn:** `D:\Document\Database\`

Thư mục này chứa các giáo trình về cơ sở dữ liệu từ cơ bản đến nâng cao, đặc biệt hữu ích cho việc thiết kế và triển khai SQLite trong dự án PressO.

#### 📚 Sách Giáo Khoa Cơ Bản

| Tài liệu | Tác giả | Nội dung chính | Ưu tiên |
|----------|---------|----------------|---------|
| **Fundamentals of Database Systems (7th Ed.)** | Elmasri & Navathe | Lý thuyết CSDL toàn diện, mô hình ER, SQL, normalization, transaction | ⭐⭐⭐ |
| **An Introduction to Database Systems** | Bipin C. Desai | Khái niệm cơ bản, data models, file organization | ⭐⭐ |

**Áp dụng cho PressO:**
- Thiết kế schema SQLite (`contracts`, `payment_stages`, `execution_history`)
- Quy tắc normalization cho local database
- Transaction management cho data integrity

#### 📚 Sách Nâng Cao

| Tài liệu | Tác giả | Nội dung chính | Ưu tiên |
|----------|---------|----------------|---------|
| **Database Systems - The Complete Book (2nd Ed.)** | Garcia-Molina, Ullman, Widom (Stanford) | Query processing, optimization, indexing, distributed DB | ⭐⭐⭐ |
| **Database Design and Implementation (2nd Ed.)** | Edward Sciore | Internals của DBMS, JDBC, SimpleDB implementation | ⭐⭐⭐ |

**Áp dụng cho PressO:**
- Hiểu cách SQLite xử lý queries
- Tối ưu hóa truy vấn cho execution history
- Thiết kế index cho tìm kiếm nhanh

#### 📚 SQLite Chuyên Sâu

| Tài liệu | Tác giả | Nội dung chính | Ưu tiên |
|----------|---------|----------------|---------|
| **Getting Started with SQLite** | Boston University | Hướng dẫn thực hành SQLite, DB Browser, JDBC | ⭐⭐⭐ |
| **SQLite Internals** | Abdur-Rahmaan Janhangeer | Kiến trúc nội bộ SQLite, file format, WAL mode, bytecodes | ⭐⭐⭐ |

**Áp dụng cho PressO:**
- Hiểu file format `.db`, WAL mode cho concurrent access
- Bytecode execution cho query understanding
- Best practices cho embedded database

#### 📚 SQL Thực Hành

| Tài liệu | Tác giả | Nội dung chính | Ưu tiên |
|----------|---------|----------------|---------|
| **Advanced SQL for Beginners** | - | Joins, aggregation, subqueries, CTEs, window functions | ⭐⭐ |

**Áp dụng cho PressO:**
- Viết queries phức tạp cho reporting
- Aggregation cho dashboard statistics

---

### 2.2 Frontend & Web Design

**Đường dẫn:** `D:\Document\Frontend\`

Tài liệu về thiết kế giao diện người dùng, HTML/CSS/JavaScript fundamentals.

| Tài liệu | Tác giả | Nội dung chính | Ưu tiên |
|----------|---------|----------------|---------|
| **Learning Web Design (5th Ed.)** | Jennifer Niederst Robbins (O'Reilly) | HTML, CSS, JavaScript, Web Graphics cơ bản đến nâng cao | ⭐⭐⭐ |
| **The Modern Web Design Process** | Webflow | Quy trình thiết kế web, sitemaps, wireframes, visual design | ⭐⭐ |
| **Web Application Architecture (2nd Ed.)** | Leon Shklar & Rich Rosen | HTTP, HTML, server-side programming, security headers | ⭐⭐⭐ |

**Áp dụng cho PressO:**
- Thiết kế UI với Electron (vẫn dùng HTML/CSS/JS)
- Hiểu HTTP protocols cho API Hub (Go)
- CSS architecture cho dark theme system

---

### 2.3 Fullstack & Kiến Trúc Hệ Thống

**Đường dẫn:** `D:\Document\Fullstack\`

Thư mục lớn nhất với nhiều chủ đề: AI/ML, hiệu năng, hệ phân tán, software engineering.

#### 🤖 AI & Machine Learning

| Tài liệu | Tác giả | Nội dung chính | Ưu tiên |
|----------|---------|----------------|---------|
| **Engineering AI Systems: Architecture and DevOps Essentials** | Bass, Lu, Weber, Zhu (2025) | Kiến trúc AI systems, MLOps, Foundation Models, testing AI | ⭐⭐⭐ |
| **Integrating AI into System-Level Design** | MathWorks | AI trong embedded systems, reduced-order modeling | ⭐⭐ |

**Áp dụng cho PressO:**
- Thiết kế Python Engine cho AI/LLM integration
- MLOps practices cho model management
- Foundation Model integration (RAG, prompt engineering)

#### ⚡ Hiệu Năng & Tối Ưu Hóa

| Tài liệu | Nội dung chính | Ưu tiên |
|----------|----------------|---------|
| **MIT 6.172 Performance Engineering (Lectures 1-23)** | Matrix multiplication, bit hacks, assembly, multicore, caching, parallel algorithms | ⭐⭐⭐ |

**Chi tiết các bài giảng quan trọng:**

| Bài | Chủ đề | Áp dụng PressO |
|-----|--------|----------------|
| Lecture 1 | Introduction & Matrix Multiplication | Hiểu performance basics |
| Lecture 2 | Bentley Rules for Optimizing Work | Tối ưu algorithms |
| Lecture 6 | Multicore Programming | Rust Engine parallel processing |
| Lecture 7 | Races and Parallelism | Thread safety |
| Lecture 10 | Measurement and Timing | Performance benchmarking |
| Lecture 11-12 | Storage Allocation | Memory management |
| Lecture 14-15 | Caching Algorithms | Cache optimization |
| Lecture 17 | Synchronization Without Locks | Lock-free programming |

**Áp dụng cho PressO:**
- Thiết kế Rust Engine với parallel processing
- Memory allocation strategies
- Cache-efficient algorithms cho document processing

#### 🌐 Hệ Thống Phân Tán

| Tài liệu | Tác giả | Nội dung chính | Ưu tiên |
|----------|---------|----------------|---------|
| **CS244b – Distributed Systems** | Stanford (Humphries & Mazières) | RPC, consensus, fault tolerance, scalability | ⭐⭐ |
| **Introduction to Distributed Systems** | Tanenbaum (TDTS04) | Distributed systems overview, communication | ⭐⭐ |

**Áp dụng cho PressO:**
- IPC communication design (UI ↔ Kernel ↔ Engines)
- RPC patterns cho module coordination
- Fault tolerance cho engine process management

#### 🛠️ Software Engineering

| Tài liệu | Tác giả | Nội dung chính | Ưu tiên |
|----------|---------|----------------|---------|
| **Software Engineering: A Practitioner's Approach (7th Ed.)** | Roger S. Pressman | SDLC, requirements, design, testing, project management | ⭐⭐⭐ |

**Áp dụng cho PressO:**
- Development methodology
- Requirements analysis
- Testing strategies
- Project phase planning

#### 📦 Khác

| Tài liệu | Nội dung chính | Ưu tiên |
|----------|----------------|---------|
| **Web Application Architecture** | Web protocols, HTTP, sessions, caching | ⭐⭐ |
| **Composing Cross-Platform Development Environment Using Maven** | Build systems | ⭐ |
| **Introduction to High Performance Scientific Computing** | Scientific computing basics | ⭐ |

---

### 2.4 Bảo Mật Server & Mạng

**Đường dẫn:** `D:\Document\Security-Server\`

Tài liệu về bảo mật mạng, server, và ứng dụng web từ góc độ infrastructure.

| Tài liệu | Tác giả | Nội dung chính | Ưu tiên |
|----------|---------|----------------|---------|
| **Cryptography and Network Security (5th Ed.)** | William Stallings | Symmetric/asymmetric ciphers, hash functions, network security protocols | ⭐⭐⭐ |
| **Network Security: A Practical Approach** | Jan L. Harrington | Firewalls, IDS, malware, DoS, practical security | ⭐⭐⭐ |
| **Web & Mobile Security (CyBOK v1.0.1)** | CyBOK | Web security fundamentals, HTTPS, same-origin policy, mobile security | ⭐⭐⭐ |

**Áp dụng cho PressO:**
- Cryptographic operations trong Rust Engine
- HTTPS/TLS cho Go API Hub
- Credential encryption (DPAPI on Windows)
- Network security cho external API calls

| Tài liệu | Tác giả | Nội dung chính | Ưu tiên |
|----------|---------|----------------|---------|
| **Secure Programming Lecture 10: Web Application Security I** | David Aspinall (Edinburgh) | OWASP Top 10, HTTP security | ⭐⭐ |
| **HELLENIC REPUBLIC Cybersecurity Guide** | Greek NCSA | National cybersecurity framework | ⭐ |

---

### 2.5 Bảo Mật Mã Nguồn

**Đường dẫn:** `D:\Document\Security-Source-Code\`

Tài liệu về lập trình an toàn (secure coding), đặc biệt quan trọng cho development.

#### 📕 Sách Tham Khảo Chính

| Tài liệu | Tác giả | Ngôn ngữ | Nội dung chính | Ưu tiên |
|----------|---------|----------|----------------|---------|
| **Writing Secure Code (2nd Ed.)** | Michael Howard & David LeBlanc (Microsoft) | C/C++, Windows | Buffer overruns, access control, crypto, threat modeling | ⭐⭐⭐ |
| **Secure Coding in C and C++ (2nd Ed.)** | Robert C. Seacord (SEI/CERT) | C/C++ | Strings, integers, files, race conditions | ⭐⭐⭐ |

**Áp dụng cho PressO:**
- Rust Engine security (memory safety)
- Native code trong Electron
- Threat modeling methodology

#### 📗 Node.js & JavaScript Security

| Tài liệu | Tác giả | Nội dung chính | Ưu tiên |
|----------|---------|----------------|---------|
| **The Node.js Security Handbook (2nd Ed.)** | Sqreen | Pre-commit hooks, templating, validation, crypto, headers | ⭐⭐⭐ |
| **Node.js Web Application Security** | CyDrill | OWASP Top 10 for Node, XSS, injection, crypto | ⭐⭐⭐ |

**Áp dụng cho PressO:**
- Electron main process security
- Input validation trong UI layer
- Security headers (CSP, HSTS)

#### 📙 OWASP & Best Practices

| Tài liệu | Tác giả | Nội dung chính | Ưu tiên |
|----------|---------|----------------|---------|
| **OWASP Secure Coding Practices - Quick Reference Guide (v2.0)** | OWASP | Checklist: input validation, output encoding, auth, session, crypto | ⭐⭐⭐ |

**Áp dụng cho PressO:**
- Input validation checklist cho mọi module
- Output encoding standards
- Authentication patterns cho API Hub
- Session management (nếu có user system)
- Cryptographic practices cho credential storage

#### 📘 Khác

| Tài liệu | Nội dung chính | Ưu tiên |
|----------|----------------|---------|
| **Secure Programming Cookbook** | O'Reilly recipes for secure coding | ⭐⭐ |
| **Web Security (websecurity.dvi)** | Academic web security | ⭐ |

---

## 3. Ma Trận Ứng Dụng Theo Module Dự Án

Bảng mapping giữa các module PressO và tài liệu tham khảo liên quan:

| Module PressO | Tài liệu ưu tiên đọc |
|---------------|----------------------|
| **Java Kernel** | Software Engineering (Pressman), Distributed Systems, Engineering AI Systems |
| **Python Engine** | Engineering AI Systems, MIT 6.172 (memory), Database Systems (queries) |
| **Rust Engine** | MIT 6.172 (performance), Secure Coding in C/C++, Cryptography (Stallings) |
| **Go API Hub** | Network Security (Harrington), Web & Mobile Security, OWASP Guide |
| **Electron UI** | Learning Web Design, Node.js Security Handbook, Web Application Architecture |
| **SQLite Storage** | SQLite Internals, Getting Started with SQLite, Database Design & Implementation |
| **Security Layer** | Writing Secure Code, OWASP Quick Reference, Cryptography (Stallings) |

---

## 4. Thứ Tự Đọc Khuyến Nghị

### Giai Đoạn 1: Foundation (Tuần 1-4)

**Mục tiêu:** Xây dựng nền tảng kiến thức

1. **Software Engineering: A Practitioner's Approach** (Chương 1-8)
   - Hiểu SDLC, requirements, basic design

2. **Database Design and Implementation** (Chương 1-5)
   - Hiểu DBMS internals cơ bản

3. **SQLite Internals** (Toàn bộ)
   - Hiểu sâu về SQLite architecture

4. **OWASP Secure Coding Practices**
   - Security mindset từ đầu

### Giai Đoạn 2: Core Development (Tuần 5-12)

**Mục tiêu:** Kiến thức cho phát triển core modules

1. **MIT 6.172 Lectures** (1, 2, 6, 7, 10)
   - Performance engineering basics

2. **Engineering AI Systems** (Chương 1-5)
   - AI integration architecture

3. **Writing Secure Code** (Part I, II)
   - Secure coding techniques

4. **Node.js Security Handbook**
   - Electron/JS security

### Giai Đoạn 3: Integration & Security (Tuần 13-20)

**Mục tiêu:** Hoàn thiện và bảo mật

1. **Cryptography and Network Security** (Chương 1-10)
   - Crypto fundamentals cho Rust Engine

2. **Network Security: A Practical Approach** (Chương 1-8)
   - Practical security cho API Hub

3. **Web & Mobile Security (CyBOK)**
   - Comprehensive security review

4. **Database Systems - The Complete Book** (Chương liên quan indexing, optimization)
   - Query optimization

### Giai Đoạn 4: Advanced & Polish (Tuần 21+)

**Mục tiêu:** Tối ưu hóa và nâng cao

1. **MIT 6.172** (Lectures còn lại)
   - Advanced optimization

2. **Distributed Systems** (CS244b)
   - Scalability patterns

3. **Fundamentals of Database Systems**
   - Deep dive khi cần

---

## 5. Chi Tiết Từng Tài Liệu

### 5.1 Database

#### Fundamentals of Database Systems (7th Edition)
- **Tác giả:** Ramez Elmasri, Shamkant B. Navathe
- **Nhà xuất bản:** Pearson (2016)
- **Đường dẫn:** `D:\Document\Database\Fundamentals of Database Systems Seventh Edition.md`
- **Nội dung:**
  - Part 1: Basic Concepts (ER model, relational model)
  - Part 2: Relational Data Model and SQL
  - Part 3: Database Design Theory
  - Part 4: Object, Object-Relational, XML
  - Part 5: Advanced Data Modeling
  - Part 6-9: Implementation, Transaction, Distributed, NoSQL
- **Độ dài:** ~39,500+ dòng
- **Phù hợp:** Foundation knowledge, schema design, SQL mastery

#### Database Systems - The Complete Book (2nd Edition)
- **Tác giả:** Hector Garcia-Molina, Jeffrey D. Ullman, Jennifer Widom (Stanford)
- **Nhà xuất bản:** Pearson (2009)
- **Đường dẫn:** `D:\Document\Database\Database Systems - The Complete Book (2nd Edition).md`
- **Nội dung:**
  - Relational modeling, E/R, UML
  - SQL programming, JDBC, PHP
  - XML, XPath, XQuery, XSLT
  - Query execution & optimization
  - Transaction, logging, concurrency
  - Parallel, distributed databases
  - Data mining, search engines
- **Độ dài:** ~30,000+ dòng
- **Phù hợp:** Query optimization, distributed concepts

#### Database Design and Implementation (2nd Edition)
- **Tác giả:** Edward Sciore (Boston College)
- **Nhà xuất bản:** Springer (2020)
- **Đường dẫn:** `D:\Document\Database\Database Design and Implementation.md`
- **Nội dung:**
  - SimpleDB: working database system
  - Disk & file management
  - Buffer management, logging
  - Transaction, concurrency control
  - Query processing, optimization
  - JDBC interfaces
- **Độ dài:** ~12,600+ dòng
- **Phù hợp:** Hiểu internals, implementation details

#### SQLite Internals
- **Tác giả:** Abdur-Rahmaan Janhangeer
- **Đường dẫn:** `D:\Document\Database\SQLite Internals.md`
- **Nội dung:**
  - SQLite history & technical context
  - File & record format
  - Rollback & WAL mode
  - Bytecode execution
  - Internal modification
- **Độ dài:** ~1,600+ dòng
- **Phù hợp:** SQLite deep dive, understanding our storage

#### Getting Started with SQLite
- **Tác giả:** Warren Mansur (Boston University)
- **Đường dẫn:** `D:\Document\Database\Microsoft Word - Getting Started with SQLite.docx.md`
- **Nội dung:**
  - SQLite overview, platforms
  - DB Browser installation
  - Creating tables
  - JDBC driver setup (Eclipse, IntelliJ)
  - Connecting & inserting rows
- **Độ dài:** ~500+ dòng
- **Phù hợp:** Quick start, practical setup

#### An Introduction to Database Systems
- **Tác giả:** Bipin C. Desai (Concordia University)
- **Nhà xuất bản:** West Publishing Company
- **Đường dẫn:** `D:\Document\Database\An Introduction to Database Systems.md`
- **Nội dung:**
  - Basic concepts, data modeling
  - Data models (ER, relational, network, hierarchical)
  - File organization
  - SQL fundamentals
- **Độ dài:** ~31,000+ dòng
- **Phù hợp:** Alternative perspective, foundational concepts

#### Advanced SQL for Beginners
- **Đường dẫn:** `D:\Document\Database\Advanced SQL for Beginners - Final.md`
- **Nội dung:**
  - SELECT, FROM, ORDER BY
  - JOINs (INNER, LEFT)
  - Aggregation, HAVING
  - CASE statements, date functions
  - Subqueries, UNION
  - Window functions, CTEs
- **Độ dài:** ~2,100+ dòng
- **Phù hợp:** SQL skill improvement, practical queries

---

### 5.2 Frontend

#### Learning Web Design (5th Edition)
- **Tác giả:** Jennifer Niederst Robbins (O'Reilly)
- **Năm:** 2018
- **Đường dẫn:** `D:\Document\Frontend\Learning Web Design.md`
- **Nội dung:**
  - Part I: Getting Started
  - Part II: HTML for Structure
  - Part III: CSS for Presentation
  - Part IV: JavaScript for Behavior
  - Part V: Web Images
- **Độ dài:** ~35,800+ dòng
- **Phù hợp:** HTML/CSS/JS fundamentals cho Electron UI

#### Web Application Architecture (2nd Edition)
- **Tác giả:** Leon Shklar & Rich Rosen
- **Nhà xuất bản:** Wiley (2009)
- **Đường dẫn:** `D:\Document\Frontend\Web Application Architecture.md`
- **Nội dung:**
  - Core Internet protocols (TCP/IP)
  - HTTP protocol details
  - HTML and its roots (SGML)
  - Web sessions, caching
  - Application servers
  - Security considerations
- **Độ dài:** ~300+ dòng (summary)
- **Phù hợp:** HTTP understanding cho API Hub, web protocols

#### The Modern Web Design Process
- **Nguồn:** Webflow
- **Đường dẫn:** `D:\Document\Frontend\CHAPTER 1.md`
- **Nội dung:**
  - Setting goals for design
  - Defining project scope
  - Creating sitemaps & wireframes
  - Content-first approach
  - Visual design creation
  - Testing and launch
- **Độ dài:** ~5,100+ dòng
- **Phù hợp:** UI/UX design process

---

### 5.3 Fullstack

#### Engineering AI Systems: Architecture and DevOps Essentials
- **Tác giả:** Len Bass, Qinghua Lu, Ingo Weber, Liming Zhu
- **Nhà xuất bản:** Pearson (2025)
- **Đường dẫn:** `D:\Document\Fullstack\Engineering AI Systems Architecture and DevOps Essentials.md`
- **Nội dung:**
  - Ch 1: Introduction, system qualities
  - Ch 2: Software engineering background
  - Ch 3: AI background
  - Ch 4: Foundation models
  - Ch 5: AI model lifecycle
  - DevOps, MLOps practices
  - Testing AI systems
  - Deployment strategies
- **Độ dài:** ~2,000+ dòng
- **Phù hợp:** AI integration architecture, Python Engine design

#### Software Engineering: A Practitioner's Approach (7th Edition)
- **Tác giả:** Roger S. Pressman
- **Nhà xuất bản:** McGraw-Hill (2010)
- **Đường dẫn:** `D:\Document\Fullstack\Software Engineering A Practitioner's Approach.md`
- **Nội dung:**
  - The software process
  - Modeling (requirements, design)
  - Quality management
  - Managing software projects
  - Advanced topics
- **Độ dài:** ~67,000+ dòng
- **Phù hợp:** Project methodology, development lifecycle

#### MIT 6.172 Performance Engineering (Full Series)
- **Nguồn:** MIT OpenCourseWare
- **Đường dẫn:** `D:\Document\Fullstack\6.172 Performance Engineering of Software Systems, Lecture *.md`
- **Số bài:** 23 lectures
- **Nội dung:**
  - Lecture 1: Introduction, Matrix Multiplication
  - Lecture 2: Bentley Rules for Optimizing
  - Lecture 3: Bit Hacks
  - Lecture 4-5: Assembly Language, C to Assembly
  - Lecture 6-8: Multicore, Races, Analysis
  - Lecture 9: Compilers
  - Lecture 10: Measurement & Timing
  - Lecture 11-12: Storage Allocation
  - Lecture 13: Cilk Runtime
  - Lecture 14-15: Caching Algorithms
  - Lecture 16-17: Nondeterminism, Lock-free
  - Lecture 18-19: DSLs, Codewalk
  - Lecture 20-23: Advanced topics
- **Phù hợp:** Rust Engine optimization, parallel processing

#### CS244b – Distributed Systems (Stanford)
- **Giảng viên:** Jack Humphries, David Mazières
- **Đường dẫn:** `D:\Document\Fullstack\CS244b – Distributed Systems.md`
- **Nội dung:**
  - Remote procedure call (RPC)
  - Consensus in asynchronous systems
  - Fault tolerance, Byzantine failure
  - Scalability techniques
  - Case studies (Google, Amazon)
- **Độ dài:** ~1,000+ dòng
- **Phù hợp:** IPC design, module coordination

#### Introduction to Distributed Systems
- **Nguồn:** TDTS04 (Tanenbaum book companion)
- **Đường dẫn:** `D:\Document\Fullstack\Introduction to distributed systems.md`
- **Nội dung:**
  - Distributed vs. decentralized
  - Communication protocols
  - Naming, synchronization
  - Consistency, replication
  - Fault tolerance
- **Độ dài:** ~770+ dòng
- **Phù hợp:** Conceptual understanding

#### Integrating AI into System-Level Design
- **Nguồn:** MathWorks
- **Đường dẫn:** `D:\Document\Fullstack\[Ebook] Integrating AI into System-Level Design.md`
- **Nội dung:**
  - AI for simulation
  - Embedded algorithm development
  - Reduced-order modeling
  - Model-Based Design integration
- **Độ dài:** ~500+ dòng
- **Phù hợp:** AI in embedded contexts

---

### 5.4 Security-Server

#### Cryptography and Network Security (5th Edition)
- **Tác giả:** William Stallings
- **Nhà xuất bản:** Pearson/Prentice Hall (2011)
- **Đường dẫn:** `D:\Document\Security-Server\THE WILLIAM STALLINGS BOOKS ON COMPUTER.md`
- **Nội dung:**
  - Part I: Symmetric Ciphers (DES, AES, modes)
  - Part II: Asymmetric Ciphers (RSA, ECC)
  - Hash functions, MACs
  - Network security protocols
  - System security
- **Độ dài:** ~30,000+ dòng
- **Phù hợp:** Rust Engine crypto, API Hub TLS

#### Network Security: A Practical Approach
- **Tác giả:** Jan L. Harrington
- **Nhà xuất bản:** Morgan Kaufmann/Elsevier (2005)
- **Đường dẫn:** `D:\Document\Security-Server\Network Security A Practical Approach.md`
- **Nội dung:**
  - Ch 1: Defining security, policy
  - Ch 2: Firewalls, architecture
  - Ch 3: Physical security
  - Ch 4: Information gathering
  - Ch 5: Root kits, IDS
  - Ch 6: Spoofing
  - Ch 7: DoS attacks
  - Ch 8: Malware
- **Độ dài:** ~10,000+ dòng
- **Phù hợp:** Practical security measures

#### Web & Mobile Security Knowledge Area (CyBOK v1.0.1)
- **Tác giả:** Sascha Fahl (editor: Emil Lupu)
- **Nguồn:** CyBOK (Cyber Security Body of Knowledge)
- **Đường dẫn:** `D:\Document\Security-Server\Web&Mobile Security Knowledge Area Version1.0.1.md`
- **Nội dung:**
  - Appification & webification trends
  - URLs, HTTP, HTML, CSS, JavaScript
  - Software & content isolation
  - Permission dialogues
  - Web PKI, HTTPS
  - Client-side security
  - Server-side security (injection, XSS, CSRF)
- **Độ dài:** ~1,000+ dòng
- **Phù hợp:** Comprehensive web security

#### Secure Programming Lecture 10: Web Application Security
- **Giảng viên:** David Aspinall (Edinburgh)
- **Đường dẫn:** `D:\Document\Security-Server\Secure Programming Lecture 10 Web Application Security I (OWASP, HTTP).md`
- **Nội dung:**
  - OWASP Top 10 overview
  - HTTP fundamentals
  - Web programming security
- **Độ dài:** ~700+ dòng
- **Phù hợp:** Quick OWASP reference

---

### 5.5 Security-Source-Code

#### Writing Secure Code (2nd Edition)
- **Tác giả:** Michael Howard & David LeBlanc (Microsoft)
- **Nhà xuất bản:** Microsoft Press (2003, updated 2015)
- **Đường dẫn:** `D:\Document\Security-Source-Code\Writing Secure Code.md`
- **Nội dung:**
  - Part I: Contemporary Security (threat modeling)
  - Part II: Secure Coding (buffer overruns, access control, crypto)
  - Part III: More Techniques (sockets, RPC, DoS, .NET)
  - Part IV: Special Topics (testing, code review)
  - Appendixes: Dangerous APIs, checklists
- **Độ dài:** ~3,900+ dòng
- **Phù hợp:** Threat modeling, Windows security

#### Secure Coding in C and C++ (2nd Edition)
- **Tác giả:** Robert C. Seacord (SEI/CERT)
- **Nhà xuất bản:** Addison-Wesley (2013)
- **Đường dẫn:** `D:\Document\Security-Source-Code\Secure Coding in C and C++.md`
- **Nội dung:**
  - Ch 1: Running with Scissors (security concepts)
  - Ch 2: Strings (buffer overflows, stack smashing)
  - Integer security
  - File I/O security
  - Race conditions
  - Format strings
- **Độ dài:** ~3,100+ dòng
- **Phù hợp:** C/C++/Rust security patterns

#### The Node.js Security Handbook (2nd Edition)
- **Tác giả:** Sqreen Team
- **Đường dẫn:** `D:\Document\Security-Source-Code\The Node. JS Security Handbook.md`
- **Nội dung:**
  - Code security (pre-commit, templating)
  - Data validation
  - Avoiding dangerous modules (fs, child_process, vm)
  - Crypto best practices
  - Security headers
  - Security linters
  - Dependencies & infrastructure
- **Độ dài:** ~480+ dòng
- **Phù hợp:** Electron security, JS best practices

#### Node.js Web Application Security (CyDrill)
- **Nguồn:** CyDrill Training
- **Đường dẫn:** `D:\Document\Security-Source-Code\Node.js web application security.md`
- **Nội dung:**
  - OWASP Top 10 2025 for Node
  - Day 1: Access control, security misconfiguration, supply chain
  - Day 2: Cryptographic failures, injection (SQL, XSS)
  - Day 3: Insecure design, authentication, integrity
- **Độ dài:** ~380+ dòng
- **Phù hợp:** Node.js security training outline

#### OWASP Secure Coding Practices - Quick Reference Guide (v2.0)
- **Tác giả:** OWASP
- **Đường dẫn:** `D:\Document\Security-Source-Code\Secure Coding Practices - Quick Reference Guide.md`
- **Nội dung:**
  - Input validation checklist
  - Output encoding
  - Authentication & password management
  - Session management
  - Access control
  - Cryptographic practices
  - Error handling & logging
  - Data protection
  - Communication security
  - Database security
  - File & memory management
- **Độ dài:** ~460+ dòng
- **Phù hợp:** Security checklist for all modules

---

## Phụ Lục: Quick Reference

### Tài Liệu Theo Mức Độ Ưu Tiên

#### ⭐⭐⭐ Bắt Buộc Đọc

1. SQLite Internals
2. Getting Started with SQLite
3. OWASP Secure Coding Practices
4. Writing Secure Code
5. Node.js Security Handbook
6. Engineering AI Systems
7. Software Engineering (Pressman)

#### ⭐⭐ Nên Đọc

1. Database Design and Implementation
2. MIT 6.172 (selected lectures)
3. Cryptography and Network Security
4. Network Security: A Practical Approach
5. Web & Mobile Security (CyBOK)

#### ⭐ Tham Khảo Khi Cần

1. Fundamentals of Database Systems
2. Database Systems Complete Book
3. Distributed Systems lectures
4. Other specialized materials

---

**Lưu ý:** Tất cả tài liệu được lưu trữ dưới dạng Markdown (`.md`) tại `D:\Document\`. Độ dài ước tính dựa trên số dòng trong file.

---

*Tài liệu này được tạo để hỗ trợ đội ngũ phát triển PressO Desktop trong việc tra cứu và học tập.*

