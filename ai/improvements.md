# Improvements & Bug Backlog

Audience: an AI agent implementing one task at a time. Each task is self-contained: problem, exact location, fix steps, acceptance criteria. Follow `ai/guidelines.md` (Lombok, `Result<S,E>`, no ternaries, unit tests mandatory, mock `FileSystem`).

Priorities: **P0** = broken feature or data-loss risk. **P1** = large-repo performance (main pain point). **P2** = cleanup/UX.

---

## P0 — Broken features

### B1. Global "Duplicates" with exactly ONE repo selected never finishes
- **Files:** `src/main/java/paxel/dedup/repo/domain/repo/DuplicateRepoProcess.java:145`, `src/main/java/paxel/dedup/infrastructure/adapter/out/web/WebDupeObserver.java:34-50`
- **Problem:** `findGroups()` computes `reportedName = names.size() > 1 || all ? "batch" : names.get(0)`. When the UI starts a batch dupe check (`POST /api/repos/dupes`) with a single repo selected, results are stored and reported under the repo name, but the frontend (`App.tsx`, `useWebSocket.ts` `dupes-finished` handler) waits for key `"batch"`. Spinner runs forever; results unreachable.
- **Fix:** In `WebDupeObserver.onGroupsReady` and `onFinished`, ignore the `repo` argument for keying and use the injected `repoName` field (it is already `"batch"` for the batch endpoint and the repo name for the single endpoint). Keep the `repo` argument only for logging.
- **Accept:** Start global Duplicates with 1 repo selected → results view appears. Single-repo dupe check (repo card button) still works.

### B2. Global "Similarity" search is broken
- **File:** `ui/src/App.tsx:607-620` (toolbar) and `App.tsx:809-819` (modal `onConfirm`)
- **Problem:** The Similarity `ToolbarDropdown` ignores the repos the user selected. The modal then calls `handleDuplicateClick(showSimilarityModal.repoName || '', threshold)`; for the global case `repoName` is `null`, producing `GET /api/repos//dupes?...` — a request to a nonexistent route. Nothing runs.
- **Fix:** Store the selected repo names when the Similarity dropdown action fires (reuse `selectedReposForDupes`). In `onConfirm`, if `isGlobal`, replicate the global Duplicates flow: `setIsLoadingGlobalDupes(true); setShowGlobalDupes(true); axios.post('/api/repos/dupes?threshold=' + threshold, selectedRepos)`. Depends on B1 for the single-selection case.
- **Accept:** Similarity via toolbar with 1 and with 2+ repos selected both produce a results view.

### [DONE] B3. Web dupe groups are unsorted → "keep first" / Auto-Delete keeps an arbitrary file
- **File:** `src/main/java/paxel/dedup/repo/domain/repo/DuplicateRepoProcess.java` — `findGroups()` vs `dupe():166-181`
- **Problem:** The CLI path (`dupe()`) sorts files within each group (image area desc, size desc, oldest first, path). The web path (`findGroups()`) does **not** sort. The UI defaults to keeping index 0 and "Auto-Delete All Remaining" hard-codes keeping index 0 (`DuplicateGroupsView.tsx:163`). So the web UI's default keep-choice is arbitrary — data-loss adjacent.
- **Fix:** Extract the group-sorting comparator from `dupe()` into a private method and apply it in `findGroups()` before `onGroupsReady`. Additionally sort the groups list itself by wasted bytes descending (group size × file size) so users see the biggest wins first.
- **Accept:** Unit test: `findGroups()` returns groups whose first entry is the highest-resolution/largest/oldest file.

### B4. Filter input in the duplicates view does nothing
- **File:** `ui/src/App.tsx:704-711`
- **Problem:** The "Filter duplicates..." text input has no state, no `onChange`, no effect. Decorative.
- **Fix (cheap):** Remove the input. **Fix (better):** add `filter` query param to `GET /api/repos/{name}/dupes/batch` in `UiServer.java` that substring-matches `relativePath` across stored groups (filter groups server-side, recompute `totalGroups`), and bind the input with a debounce that refetches batch 0.
- **Accept:** Either the input is gone, or typing filters the displayed groups.

### B5. Audio preview always fails
- **Files:** `ui/src/components/FilePreview.tsx:40-41`, `src/main/java/paxel/dedup/infrastructure/adapter/in/web/UiServer.java:119-152`
- **Problem:** Frontend renders `<audio src="/api/files/preview?...">` for `audio/*`, but the backend returns 415 for anything that isn't image/video/pdf.
- **Fix:** In the preview route, add a branch for `audio/*` that streams the file bytes with the correct content type (`ctx.contentType(mimeType); ctx.result(fileSystem.newInputStream(path))` — pass the stream, do not `readAllBytes`).
- **Accept:** An mp3 duplicate shows a working audio player in the group card.

### B6. Cancel of diff/file operations reports success while work continues
- **File:** `src/main/java/paxel/dedup/infrastructure/adapter/in/web/UiServer.java:939-960` and `repo/domain/diff/DiffProcess.java`, `repo/domain/files/FilesProcess.java`
- **Problem:** `/api/diff/cancel` interrupts the worker thread and immediately publishes `diff-finished "Cancelled"`, but `DiffProcess`/`FilesProcess` only observe interruption between repos. A long copy of one repo keeps running while the UI says cancelled.
- **Fix:** Add an `AtomicBoolean cancelled` + `cancel()` to `DiffProcess` and `FilesProcess` (mirror `DuplicateRepoProcess`), check it per file inside the copy/move/delete loops, keep a map key→process in `UiServer`, and only publish `diff-finished` from the worker when it actually stops.
- **Accept:** Cancelling a multi-GB copy stops file creation within ~1 file; UI status arrives after actual stop.

### B7. A repo named `batch` collides with global dupe results
- **File:** `src/main/java/paxel/dedup/infrastructure/adapter/in/web/UiServer.java` (`dupeResults` keys, `/api/repos/{name}/dupes/batch`)
- **Problem:** Global results are stored under the literal key `"batch"`; a repo actually named `batch` would overwrite/read them.
- **Fix:** Reject `batch` as a repo name in `DedupConfig.createRepo` name validation (simplest), and document it.
- **Accept:** `dedup repo create batch /tmp` fails with a clear error; unit test added.

### B8. Inconsistent error payloads break error toasts
- **Files:** `UiServer.java` (mixes `ctx.json(result.error())` and `Map.of("message", ...)`), `ui/src/App.tsx` (reads `error.response?.data?.description`), `ui/src/hooks/useWebSocket.ts` / `FileOperationsView.tsx` (read `.message`)
- **Problem:** Depending on the endpoint, the UI shows `undefined`/generic text instead of the real error.
- **Fix:** Standardize REST errors to `{"message": "..."}`: map `DedupError` via `Map.of("message", error.describe())` in every error branch. Update the two frontend spots reading `.description` to `.message`.
- **Accept:** Creating a repo with an invalid path shows the backend's actual message in the toast.

### B9. Changing batch size mid-review corrupts the batch math
- **File:** `ui/src/components/DuplicateGroupsView.tsx:19,32-33,246-252`
- **Problem:** `batchNumber`/`totalBatches` recompute from the *new* `batchSize` while `offset` and the loaded groups came from the old one. Header shows wrong numbers; next fetch uses mixed offsets and can skip or repeat groups (dangerous combined with delete).
- **Fix:** On batch-size change, refetch from the current group index: `fetchBatch(offset)` with the new limit, or simply `fetchBatch(0)`.
- **Accept:** Switching 100 → 50 mid-review immediately shows a consistent "Batch X of Y" and no group is skipped.

### B10. Batch fetch failure leaves a dead screen
- **File:** `ui/src/components/DuplicateGroupsView.tsx:49-53`
- **Problem:** On `fetchBatch` error only `console.error` runs; user sees an empty non-finished view with no retry.
- **Fix:** Add an `error` state; render message + "Retry" button calling `fetchBatch(offset)`.
- **Accept:** Kill the backend mid-review → error card with working retry after backend restart.

---

## P1 — Large-repo performance (main complaint)

### L1. Per-file progress events flood the WebSocket and freeze the browser
- **Files:** `src/main/java/paxel/dedup/infrastructure/adapter/out/web/WebUpdateObserver.java` (publishes on every `onHashing`/`onUnchanged`/`onDeleted` call = every file), `domain/service/EventBus.java`, `ui/src/hooks/useWebSocket.ts:209`
- **Problem:** Updating a repo with 500k files emits ~500k JSON WebSocket messages. The frontend runs `JSON.parse` + `setEvents(...)` (a React state update) **per message** — the throttle at `useWebSocket.ts:154` only covers `activeProcesses`, not `setEvents`. Result: UI freezes/laggy fans, exactly the reported symptom. Guidelines demand ~5Hz.
- **Fix (backend, primary):** In `WebUpdateObserver`, keep `volatile long lastPublish`; in `updateProgress`/`onDiscovery` only publish if ≥200ms elapsed since the last publish for this observer, but ALWAYS publish state transitions (`onScanFinished`, `onFinished`, `onError`, `onDeleted` end) and the 100% update. Unit-test with a fake clock.
- **Fix (frontend, secondary):** Move `setEvents` into the same 1s throttle used for `activeProcesses` (accumulate in a ref, flush max 1×/s, cap 50).
- **Accept:** Updating a repo with ≥100k files sends ≤10 progress messages/s (assert in `EventBusTest`-style unit test) and the page stays interactive.

### L2. `GET /api/repos` loads every repo's ENTIRE index to compute stats
- **Files:** `src/main/java/paxel/dedup/domain/service/RepoService.java:48-69`, called by `UiServer.java:67`
- **Problem:** `enrichWithStats` runs `RepoManager.load()` (reads *all* index files into HashMaps) for every repo, then `stream().filter().toList()` (materializes the whole list again just to loop). This endpoint fires on page load, on every WebSocket reconnect, and after every `finished` event (`queryClient.invalidateQueries(['repos'])`). With multi-million-entry repos this is seconds-to-minutes per call plus massive GC churn.
- **Fix:**
  1. Remove the `.toList()` — iterate the stream directly.
  2. Cache stats: keep a `ConcurrentHashMap<String, RepoStats>` in `RepoService`, invalidated only when an update/prune/delete for that repo finishes. Serve cached stats otherwise; compute lazily on first request.
  3. (Optional, durable) Persist stats into `dedup_repo.yml` at the end of update/prune so a server restart doesn't recompute.
- **Accept:** Second consecutive `GET /api/repos` returns in <50ms with no index-file reads (verify via mocked `FileSystem` interaction count).

### L3. Deleting N files reloads the full repo index N times
- **Files:** `src/main/java/paxel/dedup/infrastructure/adapter/in/web/UiServer.java:154-196` (`/api/files/delete`), same pattern in `DuplicateRepoProcess.updateRepoIndex:264-273`
- **Problem:** For each deleted file: `RepoManager.forRepo(...).load()` (full index read), mark missing, close. Deleting a 200-file batch in a 1M-entry repo = 200 full index loads → minutes per batch, memory thrash.
- **Fix:** Group the request items by `repoName`, load each repo **once**, apply all `withMissing(true)` marks, close once. Same restructuring for `deleteOthers`/`moveOthers` in `DuplicateRepoProcess` (collect per repo first).
- **Accept:** Unit test with mocked `FileSystem`: deleting 3 files of one repo triggers exactly one index load.

### L4. Duplicate results pinned in server RAM, bloated by embedded Repo objects
- **Files:** `UiServer.java:39` (`dupeResults`), `DuplicateRepoProcess.RepoRepoFile` (record embeds full `Repo` incl. `stats` with the whole mime-distribution map), `ui/src/App.tsx:688-697` (back button doesn't clean up)
- **Problem:** Each `RepoRepoFile` serializes its complete `Repo` (with stats map) *per file per group* — batch responses are huge, and the whole result set lives in `dupeResults` until the user clicks through to the finish screen. Leaving via the Back button leaks it permanently.
- **Fix:**
  1. Create a slim DTO for the batch endpoint: `{repoName, repoPath, repoFile}` — map in the `/dupes/batch` handler; adjust `ui/src/types.ts` + usages (`item.repo.name` → `item.repoName`).
  2. Call `DELETE /api/repos/{key}/dupes/results` from the Back buttons in `App.tsx` (both single and global views).
  3. Guard: cap stored results (e.g. keep only the 2 most recent keys).
- **Accept:** Batch response for a 100-group page shrinks by >50% for a stats-heavy repo; back-navigation removes the server-side entry (verify via subsequent 404).

### L5. Image previews ship full-size originals
- **Files:** `UiServer.java:136-140`, `ui/src/components/FilePreview.tsx`
- **Problem:** `is.readAllBytes()` loads e.g. a 40MB TIFF fully into heap and sends it to render a 160px card. A page of 20 groups × 4 photos = potentially >1GB transferred; heap spikes; browser decodes originals.
- **Fix:** Server-side thumbnail: read via `javax.imageio`, downscale longest edge to 512px, JPEG-encode ~80%, cache at `~/.config/dedup/cache/thumbs/<sha1(path+mtime)>.jpg`, serve with `Cache-Control: max-age=86400`. Fall back to original bytes only if decoding fails. Keep memory bounded (`ImageIO.read` on an `InputStream`, no `readAllBytes`).
- **Accept:** Preview response for a 25MB JPEG is <100KB; repeated request hits the disk cache (no re-decode).

### L6. `FileOperationsView` opens a second unthrottled WebSocket
- **File:** `ui/src/components/FileOperationsView.tsx:83-119`
- **Problem:** A second `/events` socket receives the full event stream again (including the L1 flood and the replay), parses every message, and has no reconnect logic — a dropped connection silently kills operation progress.
- **Fix:** Delete the local WebSocket. Extend `useWebSocket` to track `diffProgress: Record<string, DiffProgress>` from `diff-progress`/`diff-finished`/`diff-error` events and pass it down from `App` as a prop.
- **Accept:** Only one `/events` connection in devtools; diff progress survives a reconnect.

### L7. Similarity grouping is O(n²) with BigInteger per pair
- **File:** `src/main/java/paxel/dedup/repo/domain/repo/DuplicateRepoProcess.java:378-445` (`groupByHamming`), `473-510` (`groupByAudio`)
- **Problem:** 100k fingerprints → ~5·10⁹ pairwise comparisons, each allocating `BigInteger`s. Hours of CPU. `groupByAudio` additionally never checks `cancelled`.
- **Fix (incremental, do first):** Parse each fingerprint **once** up front into `long` (64-bit) or `long[3]` (192-bit); use `Long.bitCount(a ^ b)` summed. No allocation in the inner loop. Add `cancelled.get()` check to `groupByAudio`.
- **Fix (algorithmic, second PR):** Multi-index hashing: split the 64-bit hash into 4×16-bit bands; candidates must share at least one band for threshold ≥ ~75% — bucket by band value, only compare within buckets.
- **Accept:** Benchmark test (JMH exists in deps): 50k random 64-bit fingerprints group in <10s (long version) vs baseline.

### L8. EventBus serializes each event once per subscriber
- **Files:** `UiServer.java:975-1001`, `domain/service/EventBus.java`
- **Problem:** `writeValueAsString(event)` runs inside each WebSocket listener — N open tabs = N serializations of every event (multiplies the L1 flood).
- **Fix:** Serialize once in `publish` path: either let `EventBus` carry a lazily-computed JSON string (computed on first listener call) or wrap listeners so `UiServer` pre-serializes and sends the same string to all sessions.
- **Accept:** With 2 subscribed listeners, ObjectMapper is invoked once per event (unit test with counting mapper).

---

## P2 — Security / robustness / cleanup

### C1. Arbitrary file read & delete via API; server binds all interfaces
- **Files:** `UiServer.java:119` (preview: any absolute path), `:154` (delete: `Paths.get(repoPath, relativePath)` — traversal via `..`, and `repoPath` is client-supplied), `:1013` (`app.start(port)` binds 0.0.0.0)
- **Fix:** (1) `app.start("127.0.0.1", port)`; add `--host` CLI flag for opting into LAN. (2) In preview/delete, resolve+normalize the path and require it to start with a registered repo's `absolutePath` (look repos up server-side by `repoName`; ignore client-sent `repoPath`). Reject otherwise with 403.
- **Accept:** `GET /api/files/preview?path=/etc/passwd` → 403; delete with `relativePath=../../x` → 403; server unreachable from another machine by default.

### C2. `GET /api/repos/{name}/dupes` is a side-effecting GET
- **Fix:** Change to POST (keep GET as deprecated alias for one release); update `App.tsx` call sites.

### C3. Dead code & small frontend fixes (batch into one PR)
- `ui/src/App.tsx:87-96`: unused `useQuery(['dupes', ...], enabled:false)` — delete.
- Error IDs via `Math.random().toString(36)` (collisions) → `crypto.randomUUID()` (3 spots in `App.tsx`, 1 in `useWebSocket.ts`).
- `App.tsx:370-379`: header shows a spinner card per active process *and* floating overlays — drop the header duplicates.
- `DuplicateGroupsView.tsx:56-60`: effect keyed on `[totalGroups, dupeKey]` — a re-run with identical `totalGroups` doesn't refetch. Key on a run-id (e.g. timestamp from `dupes-finished`).
- `dupe-finished` vs `dupes-finished` event names are confusing — rename backend `dupe-finished` → `dupe-grouping-done` (update `useWebSocket.ts` union type).

### C4. Coarse global lock in the UI
- **File:** `ui/src/App.tsx` (`isAnyProcessRunning` disables all Update/Duplicates/Similarity buttons)
- **Problem:** One updating repo blocks starting work on unrelated repos, although the backend queues per repo just fine (`activeUpdates` map + 409s).
- **Fix:** Disable actions only for repos present in `activeProcesses`; keep the global dupe buttons blocked only while a dupe process runs (`activeDupeProcesses`).

### C5. Progress semantics of diff operations are incoherent
- **File:** `UiServer.java` diff/files handlers
- **Problem:** `diff-progress.total/completed` flips between "number of repos" and "number of files" within one operation; the progress bar in `FileOperationsView` jumps wildly.
- **Fix:** Namespace the payload: `{phase: "repos"|"files", reposTotal, reposDone, filesTotal, filesDone, currentFile}` and render two-level progress.

### C6. Roadmap leftovers (from `ai/roadmap.md`, still valid)
- Close `Sha1Hasher` executor properly; singleton Tika instance in `MimetypeProvider`; validate repo names against path traversal in the backend (also covers B7).
