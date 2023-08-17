package io.github.paxel.dedup;

import io.github.paxel.dedup.FileCollector.FileMessage;
import io.github.paxel.dedup.comparison.Comparison;
import io.github.paxel.dedup.comparison.ComparisonError;
import io.github.paxel.dedup.comparison.Stage;
import io.github.paxel.dedup.comparison.StagedComparison;
import lombok.NonNull;
import paxel.lib.Result;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


class LayeredMultiCompare {

    private final int layer;
    private FileMessage file;
    private Map<Comparison, LayeredMultiCompare> children;

    private final StagedComparison stagedComparison;

    LayeredMultiCompare(int i, FileMessage f, StagedComparison stagedComparison) {
        this.layer = i;
        this.file = f;
        this.stagedComparison = stagedComparison;
    }

    @NonNull Result<Duplicate, ComparisonError> add(FileMessage newFile) throws IOException {
        /**
         * another file with the same size needs to be compared
         */
        if (stagedComparison.hasStage(layer)) {
            Stage stage = stagedComparison.getStage(layer);
            // we haven't reached a full conclusion yet if there are diffs in the file of this node and
            if (children == null) {
                // the second file with the same size has arrived
                // we need to prepare the comparison and do it for the first file now
                @NonNull Result<Comparison, ComparisonError> result = stage.create(file.getPath());
                if (result.hasFailed()) {
                    return result.mapError(Function.identity());
                }
                children = new HashMap<>();
                children.put(result.getValue(), new LayeredMultiCompare(layer + 1, file, stagedComparison));
                file = null;

            }
            // calculate the hash for the new file and check if we have a match already
            @NonNull Result<Comparison, ComparisonError> result = stage.create(newFile.getPath());
            if (result.hasFailed()) {
                return result.mapError(Function.identity());
            }
            Comparison comparison = result.getValue();
            LayeredMultiCompare match = children.get(comparison);
            if (match != null) {
                // next level comparison
                return match.add(newFile);
            } else {
                // no match. this is a new file
                children.put(comparison, new LayeredMultiCompare(layer + 1, newFile, stagedComparison));
                return Result.ok(null);
            }

        } else {
            // comparison finished. they are equal
            if (!file.isReadOnly() && newFile.isReadOnly()) {
                //swap files, to keep the ro in the node
                FileMessage tmp = newFile;
                newFile = file;
                file = tmp;
            } else if (file.isReadOnly() && !newFile.isReadOnly()) {
                // not allowed to swap
            } else if (file.getPath().toString().toLowerCase().contains("copy") && newFile.getPath().toString().toLowerCase().contains("copy")) {
                //swap files, to mark the copy as duplicate
                FileMessage tmp = newFile;
                newFile = file;
                file = tmp;
            }
            return Result.ok(new Duplicate(file, newFile));
        }
    }

    public List<FileMessage> getFiles() {
        if (file != null)
            return Collections.singletonList(file);
        else
            return children.entrySet().stream()
                    .map(Map.Entry::getValue)
                    .flatMap(f -> f.getFiles().stream())
                    .collect(Collectors.toList());
    }
}
