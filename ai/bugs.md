# fixed
## video display overlaps each other
in repo dupes when video is displayed in 3 images the third image is under the next image. 
the calculation of the image and the container dont match

## error messages break the update progress display
there are some errors when hashing  being written to std.out or err and ot handled correctly. 
the change of the cursor leads to the output to slide down a line
## repo scanned via ui adds stats:null to the repo and does not show infos in the ui
the stats calculation in RepoService seems to not reflect the updated index immediately or fails to load correctly after a scan.

## the progress never finishes in the top widget
The `activeProgress` in the frontend is likely not cleared after the backend process completes.

## starting new and pressing update repo does nothing
The update process might be failing to start if the repo has never been initialized or if there's an issue with how the process is triggered in UiServer.

## dashboard not updated after scan
The WebSocket might disconnect before the 'finished' event is sent or received. The UI does not provide visibility into disconnection.
- Fixed by adding a disconnection overlay to the UI.
- Fixed by adding auto-reconnection and a full repo list refresh upon reconnection.
- Increased WebSocket idle timeout to 15 minutes to reduce unexpected disconnections.
