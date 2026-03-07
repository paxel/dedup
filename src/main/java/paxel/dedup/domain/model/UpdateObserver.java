package paxel.dedup.domain.model;

import java.nio.file.Path;

public interface UpdateObserver {
    void onDiscovery(Path path, long totalFiles, long totalDirs);

    void onScanFinished(long totalFiles, long totalDirs);

    void onHashing(Path path, long processed, long total);

    void onUnchanged(Path path, long processed, long total);

    void onDeleted(Path path, long processed, long total);

    void onFinished(Statistics stats);

    void onError(Path path, Throwable e);
}
