package io.github.paxel.dedup;

import io.github.paxel.dedup.comparison.StagedComparisonFactory;
import paxel.lintstone.api.ActorSettings;
import paxel.lintstone.api.LintStoneActor;
import paxel.lintstone.api.LintStoneActorAccessor;
import paxel.lintstone.api.LintStoneMessageEventContext;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * This actor receives all files and directories and processes them by sending
 * all files it receives or finds in directories to actors that are purely
 * responsible for one file size. When it receives an end message it tells the
 * ResultCollector actor how many actors exist. Then it forwards the end to all
 * FileComparator actors and unregisters itself
 */
public class FileCollector implements LintStoneActor {

    private static final EndMessage END = new EndMessage();
    public static final StagedComparisonFactory STAGED_COMPARISON_FACTORY = new StagedComparisonFactory();
    private final Map<Long, LintStoneActorAccessor> actors = new HashMap<>();
    private final DedupConfig cfg;

    List<Path> accessDenied;
    private int denied;
    private ArrayList<Path> errors;
    private int failed;
    private final Long start;

    private BiConsumer<DirMessage, Throwable> deniedConsumer = (d, t) -> {
    };
    private Consumer<DirMessage> dirConsumer = d -> {
    };
    private BiConsumer<Path, Boolean> fileConsumer = (f, b) -> {
    };
    private BiConsumer<DirMessage, Throwable> errorConsumer = (d, t) -> {
    };
    private int readOnlyFiles;
    private int readWriteFiles;
    private int readWrite;
    private int readOnlyDirs;
    private long fileData;
    private Throwable lastError;
    private final List<FileMessage> files = new ArrayList<>();
    private final List<CompletableFuture<Void>> resultCollector = new ArrayList<>();
    private int i;

    public FileCollector(DedupConfig cfg) {
        this.cfg = cfg;
        if (cfg.isRealVerbose()) {
            deniedConsumer = (d, t) -> {
                // store all denied dirs
                if (accessDenied == null) {
                    accessDenied = new ArrayList<>();
                }
                accessDenied.add(d.path());
            };
            errorConsumer = (d, t) -> {
                // store all failed dirs
                if (errors == null) {
                    errors = new ArrayList<>();
                }
                errors.add(d.path());
                this.lastError = t;
            };
            fileConsumer = (f, b) -> {
                if (b) {
                    readOnlyFiles++;
                } else {
                    readWriteFiles++;
                }
            };

            dirConsumer = d -> {
                if (d.readOnly) {
                    readOnlyDirs++;
                } else {
                    readWrite++;
                }
            };

        } else if (cfg.isVerbose()) {
            // count all denied dirs
            deniedConsumer = (d, t) -> denied++;
            errorConsumer = (d, t) -> failed++;
            fileConsumer = (f, b) -> {
                if (b) {
                    readOnlyFiles++;
                } else {
                    readWriteFiles++;
                }
            };

            dirConsumer = d -> {
                if (d.readOnly) {
                    readOnlyDirs++;
                } else {
                    readWrite++;
                }
            };
        }
        start = System.currentTimeMillis();
    }

    static FileMessage fileMessage(Path root, boolean readOnly) {
        return new FileMessage(readOnly, root);
    }

    static EndMessage endMessage() {
        return END;
    }

    static DirMessage dirMessage(Path root, boolean readOnly) {
        return new DirMessage(readOnly, root);
    }

    private int zero;

    @Override
    public void newMessageEvent(LintStoneMessageEventContext mec) {
        mec
                // scan dir = most often
                .inCase(DirMessage.class, this::scanDir)
                // a few files might be added
                .inCase(FileMessage.class, (f, m) -> this.addFile(f.path, f.readOnly, m))
                // all import received
                .inCase(EndMessage.class, this::end)
                // that shouldn't happen at all
                .otherwise((a, b) -> System.out.println("" + a));
    }

    private void scanDir(DirMessage dir, LintStoneMessageEventContext m) {
        try {
            dirConsumer.accept(dir);
            Files.list(dir.path()).filter(Files::isRegularFile).forEach(f -> addFile(f, dir.readOnly, m));
        } catch (AccessDeniedException ex) {
            deniedConsumer.accept(dir, ex);
        } catch (IOException ex) {
            errorConsumer.accept(dir, ex);
        }
    }

    private void addFile(Path f, boolean readOnly, LintStoneMessageEventContext m) {
        fileConsumer.accept(f, readOnly);
        long length = f.toFile().length();
        if (length == 0) {
            this.zero++;
        } else {
            fileData += length;
            final LintStoneActorAccessor actor = actors.computeIfAbsent(length, k -> m.registerActor("FileComparator_" + length, () -> new FileComparator(length),  ActorSettings.DEFAULT));
            actor.tell(fileMessage(f, readOnly));
        }
    }

    private void end(EndMessage end, LintStoneMessageEventContext m) {
        printVerbose();
        for (LintStoneActorAccessor actor : this.actors.values()) {
            // broken?
            resultCollector.add(actor.<UniqueFiles>ask(end)
                    .thenApply(this::addFiles));
        }
        CompletableFuture<Void> allAnswersReceived = CompletableFuture.allOf(resultCollector.toArray(new CompletableFuture[0]));
        allAnswersReceived.thenAccept(a -> {
            try {
                m.reply(new UniqueFiles(files));
                // we're done. BUG: if we unregister here, the actor
                m.unregister();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private Void addFiles(UniqueFiles uniqueFiles) {
        this.files.addAll(uniqueFiles.result());
//        System.out.println(++i);
        return null;
    }

    private void printVerbose() {
        if (cfg.isRealVerbose()) {
            final int deniedCount = this.accessDenied != null ? accessDenied.size() : 0;
            final int errCount = this.errors != null ? errors.size() : 0;
            System.out.println("File scan completed:\n" + "     Scanned * " + this.readOnlyDirs + " ro directories\n" + "             * " + this.readWrite + " rw directories\n" + "             * " + readOnlyFiles + " ro files\n" + "             * " + readWriteFiles + " rw files\n" + " Encountered * " + deniedCount + " access rejections\n" + "             * " + errCount + " errors\n" + "   Processed * " + this.fileData + " bytes\n" + "        With * " + this.zero + " times 0 byte files\n" + "         and * " + this.actors.size() + " different file sizes\n" + "          in * " + Duration.ofMillis(System.currentTimeMillis() - start).getSeconds() + " seconds\n");
            if (errCount > 0) {
                System.out.println("Failed:\n");
                for (Path error : errors) {
                    System.out.println("  * " + error);
                }
            }
            if (deniedCount > 0) {
                System.out.println("Access denied:\n");
                for (Path error : accessDenied) {
                    System.out.println("  * " + error);
                }
            }
            if (lastError != null) {
                System.out.println("Last error:\n");
                lastError.printStackTrace();
            }

        } else if (cfg.isVerbose()) {
            System.out.println("File scan completed:\n" + "     Scanned * " + this.readOnlyDirs + " ro directories\n" + "             * " + this.readWrite + " rw directories\n" + "             * " + readOnlyFiles + " ro files\n" + "             * " + readWriteFiles + " rw files\n" + " Encountered * " + this.denied + " access rejections\n" + "             * " + this.failed + " errors\n" + "   Processed * " + this.fileData + " bytes\n" + "        With * " + this.zero + " times 0 byte files\n" + "         and * " + this.actors.size() + " different file sizes\n" + "          in * " + Duration.ofMillis(System.currentTimeMillis() - start).getSeconds() + " seconds\n");
        }
    }

    public record EndMessage() {
    }

    public record FileMessage(boolean readOnly, Path path) {
    }

    public record DirMessage(boolean readOnly, Path path) {
    }
}
