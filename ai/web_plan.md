# Project Dedup: Web GUI Transformation Blueprint

This document serves as the **Master Instruction Set** for transitioning the Dedup CLI into a modern, local-first Web Application. It is designed to guide future autonomous agents through the implementation phases with high precision.

---

## 🏛️ Phase 1: The Embedded Engine (Backend Architecture)
**Objective**: Transform the CLI from a one-shot process into a persistent, API-driven service.

### 1.1 Embedded Server Integration
- **Framework**: Use **Javalin 6.x** (lightweight, no-magic, perfect for local tools).
- **Jackson**: Standardize JSON serialization using existing `JacksonMapperLineCodec` logic where applicable.
- **Port Strategy**: Default to `4567`, but allow override via `--port`.

### 1.2 Core Refactoring (The Service Layer)
- **RepoService**: Extract logic from `RepoCommand` into a new `RepoService`.
- **Async Events**: Implement an `EventBus` (or simple listener pattern) to decouple long-running processes (like `UpdateReposProcess`) from the CLI/Web lifecycle.
- **Lifecycle Management**: Ensure `UiServer.stop()` gracefully closes all `IndexManager` instances.

### 1.3 Communication Protocol
- **REST**: For state-changing actions (create repo, delete file, update config).
- **WebSockets**: Mandatory for the "Live Progress" dashboard.
    - **Message Format**: `{"type": "progress", "data": { ...stats... }}`.
    - **Throttle**: Limit broadcast to 5Hz to avoid flooding the browser.

---

## ⚛️ Phase 2: The Modern Interface (Frontend Architecture)
**Objective**: Build a high-performance React SPA that reflects the power of the backend.

### 2.1 Technology Stack
- **Runtime**: Node.js + Vite (Build-time only).
- **Framework**: **React 19** (Functional components, Hooks).
- **Styling**: **Tailwind CSS** + **Shadcn/UI** (for accessible, clean components).
- **State Management**: **TanStack Query** (React Query) for server-state synchronization.

### 2.2 Component Hierarchy
- `AppShell`: Sidebar navigation, global notifications.
- `Dashboard`: Overview cards (Total Size, Dupe Count, Health).
- `RepoList`: Grid of repositories with quick action buttons.
- `LiveUpdate`: Real-time progress visualization with **Recharts** for "Files/Sec" history.
- `DuplicateExplorer`: High-performance virtualized list (for 10k+ dupes) with image/video preview overlays.

---

## 🛠️ Phase 3: Implementation Instructions for Bots

### 3.1 Backend Blueprint (Java)
1. **Package**: `paxel.dedup.infrastructure.adapter.in.web`.
2. **Main Route**: `GET /api/repos` -> returns `List<Repo>`.
3. **WS Route**: `WS /api/events` -> streams `StatisticPrinter` updates.
4. **Static Assets**: Server files from `src/main/resources/static` using `app.spa.addTask()`.

### 3.2 Frontend Blueprint (React/Vite)
1. **Directory**: Root `/ui`.
2. **Build Integration**: Configure `frontend-maven-plugin` to:
    - Run `npm install` and `npm run build`.
    - Copy `ui/dist/*` to `src/main/resources/static`.
3. **API Client**: Use `axios` or `fetch` with a base URL that adapts to local environment.

---

## ✅ Phase 4: Definition of "Done" (Verification)
- [ ] `dedup --ui` launches the server and automatically opens the browser.
- [ ] The Dashboard accurately reflects the state of `dedup_repo.yml` files.
- [ ] Starting a "Repo Update" in the UI triggers the background process and streams progress via WebSocket.
- [ ] Duplicate management (Delete/Keep) in the UI correctly updates the local filesystem and index files.

---
*Status: Ready for Phase 1 Implementation*
*Updated: 2026-02-25*
