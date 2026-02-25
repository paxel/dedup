package paxel.dedup.repo.domain.diff;

import org.junit.jupiter.api.Test;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.domain.model.Repo;
import paxel.dedup.domain.model.RepoFile;
import paxel.dedup.repo.domain.repo.RepoManager;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class DiffProcessMoveTest {

    @Test
    void move_updatesSourceIndexToMissing() {
        DiffProcessSyncTest.MockRecordingFileSystem fs = new DiffProcessSyncTest.MockRecordingFileSystem();

        Path repoDir = Paths.get("/repos");
        Path aData = Paths.get("/Adata");
        Path bData = Paths.get("/Bdata");
        fs.createDirectories(repoDir);
        fs.createDirectories(aData);
        fs.createDirectories(bData);

        Repo repoA = new Repo("A", aData.toString(), 1);
        Repo repoB = new Repo("B", bData.toString(), 1);
        DiffProcessSyncTest.TestDedupConfig cfg = new DiffProcessSyncTest.TestDedupConfig(repoDir, repoA, repoB);

        RepoManager aMgr = RepoManager.forRepo(repoA, cfg, fs);
        RepoManager bMgr = RepoManager.forRepo(repoB, cfg, fs);
        assertThat(aMgr.load().hasFailed()).isFalse();
        assertThat(bMgr.load().hasFailed()).isFalse();

        // A has file that B doesn't have
        fs.putFile(aData.resolve("to_move.txt").toString(), "content");
        RepoFile aEntry = RepoFile.builder()
                .hash("H_MOVE")
                .size(7L)
                .relativePath("to_move.txt")
                .lastModified(1L)
                .missing(false)
                .mimeType("text/plain")
                .build();
        assertThat(aMgr.addRepoFile(aEntry).hasFailed()).isFalse();

        // Perform move
        DiffProcess process = new DiffProcess(new CliParameter(), "A", "B", cfg, null, fs);
        int rc = process.copy("/Bdata", true); // move = true
        assertThat(rc).isEqualTo(0);

        // Verify FS move
        assertThat(fs.ops()).anyMatch(s -> s.equals("move " + aData.resolve("to_move.txt") + " -> " + bData.resolve("to_move.txt")));

        // Verify source index updated (missing=true)
        RepoManager aReload = RepoManager.forRepo(repoA, cfg, fs);
        assertThat(aReload.load().hasFailed()).isFalse();
        RepoFile inA = aReload.getByPath("to_move.txt");
        assertThat(inA).isNotNull();
        assertThat(inA.missing()).as("Source file should be marked as missing after move").isTrue();
    }
}
