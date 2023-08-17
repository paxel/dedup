package io.github.paxel.dedup;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Getter
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class UniqueFiles {
    private final List<FileCollector.FileMessage> result;
}
