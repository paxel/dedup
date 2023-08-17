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
import java.util.function.Consumer;

/**
 * @author axel
 */
public class ResultCollector implements LintStoneActor {

    public static String NAME = "collector";
    private int readonly;
    private int deletedSuccessfully;
    private int deletionFailed;
    private Throwable lastException;
    private int readwrite;
    private Consumer<FileCollector.FileMessage> statistics = f -> {
    };
    private Consumer<FileCollector.FileMessage> action = f -> {
    };

    public ResultCollector(DedupConfig config) {
        if (config.isRealVerbose()) {
            statistics = f -> {
                System.out.println("Duplicate found: " + f.getPath() + " (" + (f.isReadOnly() ? "RO)" : "RW)"));
                if (f.isReadOnly()) {
                    readonly++;
                } else {
                    readwrite++;
                }
            };
        } else if (config.isVerbose()) {
            statistics = f -> {
                if (f.isReadOnly()) {
                    readonly++;
                } else {
                    readwrite++;
                }
            };
        }
        if ("PRINT".equalsIgnoreCase(config.getAction())) {
            action = f -> {
                if (!f.isReadOnly()) {
                    System.out.println(f.getPath());
                }
            };
        } else if ("DELETE".equalsIgnoreCase(config.getAction())) {
            action = f -> {
                if (!f.isReadOnly()) {
                    try {
                        if (Files.deleteIfExists(f.getPath())) {
                            deletedSuccessfully++;
                        }
                    } catch (IOException ex) {
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
                .inCase(FileCollector.EndMessage.class, this::end)
                .otherwise((a, b) -> {
            System.out.println("" + a);
        });
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
