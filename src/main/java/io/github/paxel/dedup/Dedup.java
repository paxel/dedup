package io.github.paxel.dedup;

import com.beust.jcommander.JCommander;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import paxel.bulkexecutor.ErrorHandler;
import paxel.lintstone.api.*;

/**
 *
 */
public class Dedup {

    private final Instant start = Instant.now();

    public static void main(String[] args) {

        Dedup dedup = new Dedup();
        final DedupConfig cfg = new DedupConfig();
        final JCommander.Builder addObject = JCommander.newBuilder()
                .addObject(cfg);
        final JCommander build = addObject.build();
        build.parse(args);

        if (cfg.getSafe().isEmpty() && cfg.getUnsafe().isEmpty()) {
            build.usage();
            System.exit(2);
        }

        if (cfg.isRealVerbose()) {
            System.out.println("" + cfg);
        }
        try {
            dedup.run(cfg);
        } catch (InterruptedException ex) {
            Logger.getLogger(Dedup.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void run(DedupConfig cfg) throws InterruptedException {

        LintStoneSystem system = LintStoneSystemFactory.create(Executors.newCachedThreadPool());
        // all files are processed by this actor
        ActorSettings fileCollectorSettings = ActorSettings.create()
                .setMulti(false)
                .setBlocking(true)
                .setLimit(1000)
                .setBatch(1)
                .build();
        LintStoneActorAccess fileCollector = system.registerActor("fileCollector", () -> new FileCollector(cfg), Optional.empty(), fileCollectorSettings);

        // This actor collects all the results of the system
        ResultCollector resultCollector = new ResultCollector(cfg);
        ActorSettings resultCollectorSettings = ActorSettings.create()
                .setMulti(true)
                .setBlocking(true)
                .setLimit(1000000)
                .setBatch(1000)
                .build();
        LintStoneActorAccess lintStoneActorAccess = system.registerActor(ResultCollector.NAME, () -> resultCollector, Optional.empty(), resultCollectorSettings);

        // verify that no paths are overlapping, because that would lead to duplicates and worst case deleted safe files
        checkSafeUnsafeOverlapping(cfg);
        checkOverlapping("safe", cfg.getSafe());
        checkOverlapping("unsafe", cfg.getUnsafe());
        // put all safe files in
        processParameter(true, fileCollector, cfg.getSafe());
        // put all unsafe files in
        processParameter(false, fileCollector, cfg.getUnsafe());

        final CountDownLatch uniqueCollected = new CountDownLatch(1);
        AtomicReference<UniqueFiles> files = new AtomicReference<>();
        // request unique Files from Collector
        fileCollector.ask(FileCollector.endMessage(), f -> f.inCase(UniqueFiles.class, (uniqueFiles, eventContext) -> {
            files.set(uniqueFiles);
            uniqueCollected.countDown();
        }));

        uniqueCollected.await();
        final CountDownLatch statsCollected = new CountDownLatch(1);
        lintStoneActorAccess.ask(FileCollector.endMessage(), f -> f.inCase(Stats.class, (stats, mec) -> {
            printStats(stats, cfg);
            statsCollected.countDown();
        }));

        statsCollected.await();
        // wait for the result
        system.shutDownAndWait();
        if (cfg.isVerbose() || cfg.isRealVerbose()) {
            System.out.println("Finished");
        }

    }

    private void printStats(Stats stats, DedupConfig config) {

        if (config.isRealVerbose() || config.isVerbose()) {
            System.out.println(
                    "Finished processing:\n"
                            + "         * " + stats.getReadonly() + " read only duplicate files\n"
                            + "         * " + stats.getReadwrite() + " read/write duplicate files\n"
                            + "      in * " + Duration.between(start, Instant.now()) + "\n"
                            + " deleted * " + stats.getDeletedSuccessfully() + " deleted\n"
                            + "  failed * " + stats.getDeletionFailed() + " failed\n"
            );

            if (stats.getLastException() != null) {
                stats.getLastException().printStackTrace();
            }

        }
    }

    private void checkOverlapping(final String type, List<String> paths) throws UnregisteredRecipientException {
        for (int i = 0; i < paths.size(); i++) {
            for (int j = 0; j < paths.size(); j++) {
                if (i == j) {
                    continue;
                }
                final Path path1_ = Paths.get(paths.get(i));
                final Path path2_ = Paths.get(paths.get(j));
                if (path1_.startsWith(path2_) || path2_.startsWith(path1_)) {
                    System.err.println("ERROR: The given " + type + " Paths '" + path1_ + "' and '" + path2_ + "' are overlapping.");
                    System.exit(1);
                }
            }
        }
    }

    private void checkSafeUnsafeOverlapping(DedupConfig cfg) {
        for (String safe : cfg.getSafe()) {
            for (String unsafe : cfg.getUnsafe()) {
                final Path safePath = Paths.get(safe);
                final Path unsafePath = Paths.get(unsafe);
                if (safePath.startsWith(unsafePath) || unsafePath.startsWith(safePath)) {
                    System.err.println("ERROR: The safe Path '" + safe + "' and the unsafe Path '" + unsafe + "' are overlapping.");
                    System.exit(1);
                }
            }
        }
    }

    private void processParameter(boolean readOnly, LintStoneActorAccess fileCollector, List<String> list) throws
            UnregisteredRecipientException {
        for (String string : list) {
            Path root = Paths.get(string);
            if (Files.isDirectory(root)) {
                // send all dirs to the filer
                recurse(fileCollector, root, readOnly);
            } else {
                // send one file to the filer
                fileCollector.send(FileCollector.fileMessage(root, readOnly));
            }
        }
    }

    private void recurse(LintStoneActorAccess fileCollector, Path root, boolean readOnly) {
        try {
            Files.list(root)
                    .filter(Files::isDirectory)
                    .forEach(f -> recurse(fileCollector, f, readOnly));
            fileCollector.send(FileCollector.dirMessage(root, readOnly));
        } catch (AccessDeniedException ex) {
            System.err.println(root + ": access denied");
        } catch (IOException ex) {
            System.err.println(root + ": " + ex);
        }
    }

}
