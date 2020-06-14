package io.github.paxel.dedup;

import io.github.paxel.dedup.FileCollector.FileMessage;
import java.io.IOException;
import paxel.lintstone.api.LintStoneActor;
import paxel.lintstone.api.LintStoneMessageEventContext;

/**
 *
 */
public class FileComparator implements LintStoneActor {

    private final long length;
    private Node root = null;
    int lvl = 0;

    FileComparator(long length) {
        this.length = length;
    }

    @Override
    public void newMessageEvent(LintStoneMessageEventContext mec) {
        mec.inCase(FileCollector.FileMessage.class, this::addFile)
                .inCase(FileCollector.EndMessage.class, this::end);
    }

    private void addFile(FileCollector.FileMessage f, LintStoneMessageEventContext m) {
        if (root == null) {
            root = new Node(0, f, new FileHasher(length));
        } else {
            try {
                // if the new file is a duplicate the add will call handleDuplicate
                FileMessage duplicate = root.add(f);
                if (duplicate != null) {
                    m.send(ResultCollector.NAME, duplicate);
                }

            } catch (IOException ex) {
                // todo
            }
        }
    }

    private void end(FileCollector.EndMessage end, LintStoneMessageEventContext m) {
        // we're done
        m.send(ResultCollector.NAME, end);
        // we're done
        m.unregister();
    }

}
