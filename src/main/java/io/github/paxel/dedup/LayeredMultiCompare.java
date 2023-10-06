package io.github.paxel.dedup;

import io.github.paxel.dedup.FileCollector.FileMessage;
import io.github.paxel.dedup.comparison.Comparison;
import io.github.paxel.dedup.comparison.ComparisonError;
import io.github.paxel.dedup.comparison.Stage;
import io.github.paxel.dedup.comparison.StagedComparison;
import paxel.lib.Result;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    Result<Duplicate, ComparisonError> add(FileMessage newFile) {
        /**
         * another file with the same size needs to be compared
         */
        if (stagedComparison.hasStage(layer)) {
            Stage stage = stagedComparison.getStage(layer);
            // we haven't reached a full conclusion yet if there are diffs in the file of this node and
            if (children == null) {
                // the second file with the same size has arrived
                // we need to prepare the comparison and do it for the first file now
                Result<Comparison, ComparisonError> result = stage.create(file.path());
                if (result.hasFailed()) {
                    return result.mapError(Function.identity());
                }
                children = new HashMap<>();
                children.put(result.value(), new LayeredMultiCompare(layer + 1, file, stagedComparison));
                file = null;

            }
            // calculate the hash for the new file and check if we have a match already
            Result<Comparison, ComparisonError> result = stage.create(newFile.path());
            if (result.hasFailed()) {
                return result.mapError(Function.identity());
            }
            Comparison comparison = result.value();
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
            if (!file.readOnly() && newFile.readOnly()) {
                //swap files, to keep the ro in the node
                FileMessage tmp = newFile;
                newFile = file;
                file = tmp;
            } else if (file.readOnly() && !newFile.readOnly()) {
                // not allowed to swap
            } else if (file.path().toString().toLowerCase().contains("copy") && newFile.path().toString().toLowerCase().contains("copy")) {
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
            return children.values().stream()
                    .flatMap(f -> f.getFiles().stream())
                    .collect(Collectors.toList());
    }
}
