package io.github.paxel.dedup;

import io.github.paxel.dedup.FileCollector.FileMessage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 *
 */
class Node {

    private final int layer;
    private FileMessage file;
    private Map<String, Node> children;
    private final FileHasher hasher;

    Node(int i, FileMessage f, FileHasher hasher) {
        this.layer = i;
        this.file = f;
        this.hasher = hasher;
    }

    FileMessage add(FileMessage newFile) throws IOException {
        if (layer == FileHasher.MAX) {
            // comparison finished. they are equal
            if (!file.isReadOnly() && newFile.isReadOnly()) {
                //swap files, to keep the ro in the node
                FileMessage tmp = newFile;
                newFile = file;
                file = tmp;
            }
            return newFile;
        } else {
            // we need to compare the file to the other(s)
            if (children == null) {
                // this is only the second file in this layer. we need to add a layer. so first calc the hash for the previous file
                String hash1 = hasher.calc(layer, file);
                children = new HashMap<>();
                children.put(hash1, new Node(layer + 1, file, hasher));
                file = null;
            }
            // calculate the hash for the new file and check if we have a match already
            String hash2 = hasher.calc(layer, newFile);
            Node match = children.get(hash2);
            if (match != null) {
                // next level comparison
                return match.add(newFile);
            } else {
                // no match. this is a new file
                children.put(hash2, new Node(layer + 1, newFile, hasher));
                return null;
            }
        }
    }

}
