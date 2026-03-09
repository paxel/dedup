package paxel.dedup.domain.model;

import java.nio.file.Path;

public interface UpdateObserver {
    void onDiscovery(Path path, long totalFiles, long totalDirs);

    void onScanFinished(long totalFiles, long totalDirs);

    void onHashing(Path path, long processed, long total, boolean scanningActive);

    void onUnchanged(Path path, long processed, long total, boolean scanningActive);

    void onDeleted(Path path, long processed, long total);

    void onFinished(Statistics stats);

    void onError(Path path, Throwable e);

    UpdateObserver NOOP = new UpdateObserver() {
        @Override
        public void onDiscovery(Path path, long totalFiles, long totalDirs) {
        }

        @Override
        public void onScanFinished(long totalFiles, long totalDirs) {
        }

        @Override
        public void onHashing(Path path, long processed, long total, boolean scanningActive) {
        }

        @Override
        public void onUnchanged(Path path, long processed, long total, boolean scanningActive) {
        }

        @Override
        public void onDeleted(Path path, long processed, long total) {
        }

        @Override
        public void onFinished(Statistics stats) {
        }

        @Override
        public void onError(Path path, Throwable e) {
        }
    };
}
