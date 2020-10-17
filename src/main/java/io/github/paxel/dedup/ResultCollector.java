/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package io.github.paxel.dedup;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import paxel.lintstone.api.LintStoneActor;
import paxel.lintstone.api.LintStoneMessageEventContext;

/**
 *
 * @author axel
 */
public class ResultCollector implements LintStoneActor {

    public static String NAME = "collector";
    CountDownLatch countDownLatch = new CountDownLatch(1);
    private Integer countDown;
    private int readonly;
    private int deletedSuccessfully;
    private int deletionFailed;
    private Throwable lastException;
    private int readwrite;
    private final DedupConfig config;
    private Consumer<FileCollector.FileMessage> statistics = f -> {
    };
    private Consumer<FileCollector.FileMessage> action = f -> {
    };
    private Long start;

    public ResultCollector(DedupConfig config) {
        this.config = config;
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
        start = System.currentTimeMillis();
    }

    @Override
    public void newMessageEvent(LintStoneMessageEventContext mec) {
        mec
                .inCase(Integer.class, this::setActors)
                .inCase(FileCollector.FileMessage.class, this::addFile)
                .inCase(FileCollector.EndMessage.class, this::end)
                .otherwise((a, b) -> {
                    System.out.println("" + a);
                });
    }

    void awaitResult() throws InterruptedException {
        countDownLatch.await();
    }

    private void addFile(FileCollector.FileMessage f, LintStoneMessageEventContext m) {
        statistics.accept(f);
        action.accept(f);
    }

    private void end(FileCollector.EndMessage f, LintStoneMessageEventContext m) {
        countDown--;
        if (countDown <= 0) {

            if (config.isRealVerbose() || config.isVerbose()) {
                System.out.println(
                        "Finished processing:\n"
                        + "         * " + readonly + " read only duplicate files\n"
                        + "         * " + readwrite + " read/write duplicate files\n"
                        + "      in * " + ((System.currentTimeMillis() - start)/1000) + " seconds\n"
                        + " deleted * " + deletedSuccessfully + " deleted\n"
                        + "  failed * " + deletionFailed + " failed\n"
                );

                if (lastException != null) {
                    lastException.printStackTrace();
                }
            }
            // we're done
            m.unregister();
            countDownLatch.countDown();
        }
    }

    private void setActors(Integer f, LintStoneMessageEventContext m) {
        this.countDown = f;
    }

}
