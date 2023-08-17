package io.github.paxel.dedup;

import io.github.paxel.dedup.FileCollector.FileMessage;

import java.io.IOException;

import io.github.paxel.dedup.comparison.ComparisonError;
import io.github.paxel.dedup.comparison.StagedComparison;
import io.github.paxel.dedup.comparison.StagedComparisonFactory;
import lombok.NonNull;
import paxel.lib.Result;
import paxel.lintstone.api.LintStoneActor;
import paxel.lintstone.api.LintStoneMessageEventContext;

/**
 *
 */
public class FileComparator implements LintStoneActor {

    private final long length;
    private @NonNull StagedComparison stagedComparison;
    private LayeredMultiCompare root = null;
    int lvl = 0;

    FileComparator(long length, StagedComparisonFactory stagedComparisonFactory) {
        this.length = length;
        this.stagedComparison = stagedComparisonFactory.createRaw(length);
    }

    /**
     * Convert messages to calls
     *
     * @param mec The context, containing the message and access to the Actor
     *            system.
     */
    @Override
    public void newMessageEvent(LintStoneMessageEventContext mec) {
        mec.inCase(FileCollector.FileMessage.class, this::addFile)
                .inCase(FileCollector.EndMessage.class, this::end);
    }

    private void addFile(FileCollector.FileMessage f, LintStoneMessageEventContext m) {
        try {
            if (root == null) {
                root = new LayeredMultiCompare(0, f, stagedComparison);
            } else {
                try {
                    // if the new file is a duplicate the add will call handleDuplicate
                    Result<Duplicate, ComparisonError> duplicate = root.add(f);
                    if (duplicate.isSuccess()) {
                        if (duplicate.getValue() != null) {
                            System.out.println("found duplicate "
                                    + duplicate.getValue().getOriginal().getPath()
                                    + " = "
                                    + duplicate.getValue().getDuplicate().getPath());
                            m.send(ResultCollector.NAME, duplicate.getValue());
                        }
                    } else
                        System.err.println(duplicate.getError());

                } catch (IOException ex) {
                    // todo
                }
            }

        } catch (
                Exception e) {
            e.printStackTrace();
        }

    }

    private void end(FileCollector.EndMessage end, LintStoneMessageEventContext m) {
        // we're done
        m.reply(uniqueFiles());
        // we're done
        m.unregister();
    }

    private UniqueFiles uniqueFiles() {
        UniqueFiles uniqueFiles = new UniqueFiles(root.getFiles());
        return uniqueFiles;
    }

}
