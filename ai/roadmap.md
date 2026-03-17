# Dedup Project Roadmap

This document outlines the planned improvements and features for the Dedup project. It consolidates previous plans (`improvement-plan.md`, `plan.md`, `split.md`, `web_plan.md`).

## 1. Technical Improvements & Bug Fixes
- **Resource Management**: 
    - [ ] Ensure `Sha1Hasher` executor is closed properly.
    - [ ] Optimize `MimetypeProvider`: Use a singleton Tika instance instead of creating one per file.
- **Security**:
    - [ ] Validate repository names to prevent path traversal (e.g., restrict to alphanumeric).
- **UX & Progress**:
    - [ ] Improve progress calculation to show discovery status during initial scan.
- **Architecture**:
    - [x] Separate Domain logic from UI output using `UpdateObserver` (Completed).
    - [x] Implement `EventBus` for streaming state to the Web UI (Completed).

## 2. Multi-Type Similarity Detection
- **Video Similarity**:
    - [x] Metadata-based duration comparison.
    - [x] Frame extraction for visual verification.
    - [ ] Implement 3-frame "Temporal Hash" for automated similarity.
- **Audio Similarity**:
    - [x] Metadata-based comparison (duration, samplerate).
    - [ ] Implement "Content Chunk Hashing" for better accuracy.
- **PDF Similarity**:
    - [x] Page count and metadata comparison.
    - [ ] Implement text content hashing.

## 3. Web GUI Transformation (Phase 2/3)
- [x] Embedded Javalin server with REST and WebSockets.
- [x] Basic React SPA with repository list and live progress.
- [ ] **Next Steps**:
    - [ ] Improve Duplicate Explorer with virtualized lists for large datasets.
    - [ ] Add side-by-side metadata comparison in the UI.
    - [ ] Enhance "Delete/Keep" interactive workflow in the browser.

## 4. Completed Milestones
- [✓] Hexagonal Architecture setup.
- [✓] WebSocket replay for persistent UI state.
- [✓] Image fingerprinting and similarity grouping.
- [✓] Maven integration for frontend builds.

---
*Last Updated: 2026-03-17*
