# fixed
## video display overlaps each other
in repo dupes when video is displayed in 3 images the third image is under the next image. 
the calculation of the image and the container dont match

## error messages break the update progress display
there are some errors when hashing  being written to std.out or err and ot handled correctly. 
the change of the cursor leads to the output to slide down a line
## repo scanned via ui adds stats:null to the repo and does not show infos in the ui
the stats calculation in RepoService seems to not reflect the updated index immediately or fails to load correctly after a scan.

## UI stuck during scan
The UI appeared "stuck" when processing slow files (videos/images) because heavy-lifting was done on the directory walker thread.
- Fixed by refactoring `RepoManager.addPath` to perform all metadata and fingerprinting asynchronously.
- Added `currentFile` feedback to the `ProgressUpdate` DTO and UI overlay.
- Disabled conflicting repository actions when a process is already active.
- Added a "Command Bar" for multi-repo scans.

## the progress never finishes in the top widget
The `activeProgress` in the frontend is likely not cleared after the backend process completes.

## starting new and pressing update repo does nothing
The update process might be failing to start if the repo has never been initialized or if there's an issue with how the process is triggered in UiServer.

## dashboard not updated after scan
The WebSocket might disconnect before the 'finished' event is sent or received. The UI does not provide visibility into disconnection.
- Fixed by adding a disconnection overlay to the UI.
- Fixed by adding auto-reconnection and a full repo list refresh upon reconnection.
- Increased WebSocket idle timeout to 15 minutes to reduce unexpected disconnections.

## UI flickering and layout shifts during scan
The update process was flickering because scanning and processing shared the same UI space, and elements resized based on content.
- Separated scanning and processing feedback into dedicated, stable UI slots.
- Fixed layout shifts by using minimum widths and fixed heights for status elements.
- Improved backend progress updates to avoid "progress jumping" by removing redundant 0-value resets during the scan phase.
- Removed "smooth movement" interpolation that caused progress bar to lag or "masturbate".
- Added guard in React `setActiveProgress` to ignore out-of-order/stale progress updates (where percentage regresses).

## Directory Browser limitations
The previous directory browser was too simple and lacked power-user features.
- Replaced Java Swing with integrated Web Directory Browser.
- Added "Show Hidden" toggle to see dot-directories.
- Added Grid/List view toggle (default grid/tiles).
- Implemented clickable Breadcrumbs for fast navigation to any parent directory.

## Index corruption with compression
Compressed index files could become corrupt if the GZIP stream was not closed properly (e.g., process crash).
- Removed compression support for index files entirely to ensure data integrity and simplify architecture.
- Replaced GZIP-based streams with plain text (JSON/MessagePack) streams.
- Updated `Repo` model, `IndexManager`, `FrameIteratorFactoryFactory`, `RepoService`, and `DedupConfig` to remove all compression logic and variables.
- Removed `--compressed` option from CLI and corresponding UI elements in the web frontend.
- Synchronized `IndexManager.add()` to prevent concurrent write corruption.

## Java Swing directory picker in Web UI
The directory picker was a blocking Java Swing dialog, which is not suitable for a web application and might not work on headless servers.
- Replaced the Swing-based `JFileChooser` with a custom Web API for directory browsing.
- Implemented a React-based `BrowserModal` in the frontend for a seamless, consistent user experience.
