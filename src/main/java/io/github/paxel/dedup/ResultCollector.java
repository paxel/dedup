/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package io.github.paxel.dedup;

import paxel.lintstone.api.LintStoneActor;
import paxel.lintstone.api.LintStoneMessageEventContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

/**
 * @author axel
 */
public class ResultCollector implements LintStoneActor {

    public static final String NAME = "collector";
    private int readonly;
    private int deletedSuccessfully;
    private int deletionFailed;
    private Throwable lastException;
    private int readwrite;
    private Consumer<FileCollector.FileMessage> statistics = f -> {
    };
    private Consumer<FileCollector.FileMessage> action = f -> {
    };
    private Consumer<Duplicate> statisticsD = f -> {
    };
    private Consumer<Duplicate> actionD = f -> {
    };

    public ResultCollector(DedupConfig config) throws IOException {
        if (config.isRealVerbose()) {
            statistics = f -> {
                System.out.println("Duplicate found: " + f.path() + " (" + (f.readOnly() ? "RO)" : "RW)"));
                if (f.readOnly()) {
                    readonly++;
                } else {
                    readwrite++;
                }
            };
            statisticsD = f -> {
                System.out.printf("Duplicate found:%norg:(%s%s)%ndup:(%s%s)%n", f.original().readOnly() ? "RO" : "RW", f.original().path(), f.original().readOnly() ? "RO" : "RW", f.original().path());
                if (f.duplicate().readOnly()) {
                    readonly++;
                } else {
                    readwrite++;
                }

            };
        } else if (config.isVerbose()) {
            statistics = f -> {
                if (f.readOnly()) {
                    readonly++;
                } else {
                    readwrite++;
                }
            };
            statisticsD = f -> {
                if (f.duplicate().readOnly()) {
                    readonly++;
                } else {
                    readwrite++;
                }
            };
        } else if (config.getAction() == Action.PRINT) {
            action = f -> {
                String prefix = f.readOnly() ? "[RO]" : "[RW]";
                System.out.println(prefix + f.path());
            };
            actionD = f -> {
                System.out.printf("Duplicate found:%norg:(%s%s)%ndup:(%s%s)%n", f.original().readOnly() ? "RO" : "RW", f.original().path(), f.original().readOnly() ? "RO" : "RW", f.original().path());
            };
        }
        if (config.getAction() == Action.MOVE) {
            if (config.getTargetDir() != null) {
                Path path = Paths.get(config.getTargetDir());
                if (!Files.exists(path)) {
                    Files.createDirectories(path);
                }
                action = f -> {
                    if (!f.readOnly()) {
                        try {
                            Files.move(f.path(), path.resolve(f.path().getFileName()));
                            System.out.println("Moved " + f.path());
                        } catch (IOException e) {
                            System.err.println("Could not move " + f.path() + " to " + path);
                            e.printStackTrace();
                        }
                    }
                };
                actionD = f -> {
                    if (!f.duplicate().readOnly()) {
                        try {
                            Files.move(f.duplicate().path(), path.resolve(f.duplicate().path().getFileName()));
                            System.out.println("Moved " + f.duplicate().path());
                        } catch (IOException e) {
                            System.err.println("Could not move " + f.duplicate().path() + " to " + path);
                            e.printStackTrace();
                        }
                    }
                };
            }
        } else if (config.getAction() == Action.DELETE) {
            action = f -> {
                if (!f.readOnly()) {
                    try {
                        if (Files.deleteIfExists(f.path())) {
                            System.out.println("deleted " + f.path());
                            deletedSuccessfully++;
                        } else {
                            System.out.println("not deleted " + f.path());
                        }
                    } catch (IOException ex) {
                        System.out.println("failed to deleted " + f.path());
                        lastException = ex;
                        deletionFailed++;
                    }
                }
            };
            actionD = f -> {
                if (!f.duplicate().readOnly()) {
                    try {
                        if (Files.deleteIfExists(f.duplicate().path())) {
                            System.out.println("deleted " + f.duplicate().path());
                            deletedSuccessfully++;
                        } else {
                            System.out.println("not deleted " + f.duplicate().path());
                        }
                    } catch (IOException ex) {
                        System.out.println("failed to deleted " + f.duplicate().path());
                        lastException = ex;
                        deletionFailed++;
                    }
                }
            };
        }
    }

    @Override
    public void newMessageEvent(LintStoneMessageEventContext mec) {
        mec.inCase(FileCollector.FileMessage.class, this::addFile)
                .inCase(Duplicate.class, this::addDuplicate)
                .inCase(FileCollector.EndMessage.class, this::end)
                .otherwise((a, b) -> System.out.println("Result Collector: unknown message: " + a));
    }

    private void addDuplicate(Duplicate f, LintStoneMessageEventContext lintStoneMessageEventContext) {
        statisticsD.accept(f);
        actionD.accept(f);
    }


    private void addFile(FileCollector.FileMessage f, LintStoneMessageEventContext m) {
        statistics.accept(f);
        action.accept(f);
    }

    private void end(FileCollector.EndMessage f, LintStoneMessageEventContext m) {
        // we're done
        m.reply(Stats.builder()
                .setDeletionFailed(deletionFailed)
                .setReadonly(readonly)
                .setReadwrite(readwrite)
                .setLastException(lastException)
                .setDeletedSuccessfully(deletedSuccessfully)
                .build());
        m.unregister();
    }


}
