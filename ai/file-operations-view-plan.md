# File Operations View — Implementation Plan

## Overview
Add a new **File Operations** view accessible via a nav button between "Overview" and "Add Repository". This view provides a source/target repo selection layout with a command toolbar. The first command is **Copy Diff To** (maps to CLI `diff cp`).

The view is designed to be extensible for future commands: `files rm`, `files mv`, and `dupes` commands.

## Layout

```
┌─────────────────────────────────────────────────────────────┐
│  [Command Bar]                                              │
│  [ Copy Diff To ▼ ]  [ (future: Remove) ] [ (future: Move)]│
├────────────────────────────┬────────────────────────────────┤
│  SOURCE REPOS              │  TARGET REPOS                  │
│  ☑ repo_a                  │  ☐ repo_a                      │
│  ☑ repo_b                  │  ☑ repo_b                      │
│  ☐ repo_c                  │  ☐ repo_c                      │
├────────────────────────────┴────────────────────────────────┤
│  [Execute] button — triggers selected command               │
│  (for Copy Diff To: opens folder browser for target dir)    │
└─────────────────────────────────────────────────────────────┘
```

## Semantics of "Copy Diff To"

- **Source repos** (left, multi-select): The repos whose files we want to copy.
- **Target repos** (right, single-select): The reference repo to diff against.
- For each source repo, find files that exist in source but NOT in the target repo, then copy those diff files to a user-selected target directory on disk.
- Maps to CLI: `dedup diff cp <source> <reference> <targetDir> [-f filter]`
- One `diff cp` call per source repo, all diffed against the same single target (reference) repo.

> **Note**: Target side should be **single-select** because `diff cp` diffs source against one reference. Multiple references would have ambiguous semantics (union? intersection?). Keep it simple.

## Implementation Steps

### 1. Backend — New endpoint `POST /api/diff/cp`

**File**: `UiServer.java`

Request body:
```json
{
  "sourceRepos": ["repo_a", "repo_b"],
  "referenceRepo": "repo_c",
  "targetDir": "/path/to/output",
  "filter": null
}
```

- For each source repo, create a `DiffProcess` and call `.copy(targetDir, false)`.
- Run async, publish progress/completion via EventBus.
- Return 202 Accepted.

### 2. Frontend — New component `FileOperationsView.tsx`

**File**: `ui/src/components/FileOperationsView.tsx`

- Two-panel layout: left = source repos (checkboxes, multi-select), right = target repo (radio buttons, single-select).
- Command bar at top with "Copy Diff To" button (extensible for future commands).
- Optional filter text input.
- "Execute" button that:
  1. Opens the BrowserModal to pick a target directory.
  2. On directory selection, calls `POST /api/diff/cp` with the selected repos and target dir.
- Shows status/progress feedback.

### 3. Frontend — Navigation in `App.tsx`

- Add `activeView` state: `'overview' | 'fileops'` (or use existing pattern with a boolean like `showFileOps`).
- Add nav button between "Overview" and "Add Repository" in the header.
- When `showFileOps` is true, render `FileOperationsView` instead of the repo list.
- Pass `repos`, `isAnyProcessRunning`, `openBrowser`, and event bus state as props.

### 4. Future Extensibility

The command bar in `FileOperationsView` is a row of buttons. Currently only "Copy Diff To" is active. Future commands:
- **Remove Duplicates** (`diff rm`): source repos, reference repo — deletes files in source that exist in reference.
- **Move Diff To** (`diff mv`): same as copy but moves instead of copies.
- **Dupes commands**: will reuse the same source/target selection pattern.

Each command may slightly change the selection rules (e.g., some may allow multi-select on target side). The component should accept a `command` state that controls behavior.

## Files to Create/Modify

| File | Action |
|------|--------|
| `ui/src/components/FileOperationsView.tsx` | **Create** — new view component |
| `ui/src/App.tsx` | **Modify** — add nav button, state, render FileOperationsView |
| `UiServer.java` | **Modify** — add `POST /api/diff/cp` endpoint |

## Open Questions (Resolved)

- **Target side multi-select?** → No, single-select. The diff is always against one reference repo.
