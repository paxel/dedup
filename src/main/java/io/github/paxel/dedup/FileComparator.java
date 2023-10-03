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
import paxel.lintstone.api.NoSenderException;
import paxel.lintstone.api.UnregisteredRecipientException;

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
                .inCase(FileCollector.EndMessage.class, this::end)
                .otherwise((o, m) -> System.out.println("FileComparator unknown message: " + o.getClass() + " " + o));
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
                            m.send(ResultCollector.NAME, duplicate.getValue().getDuplicate());
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
        try {
            // we're done
            UniqueFiles result = uniqueFiles();
            m.reply(result);

            // we're done
            m.unregister();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private UniqueFiles uniqueFiles() {
        return new UniqueFiles(root.getFiles());
    }

}
