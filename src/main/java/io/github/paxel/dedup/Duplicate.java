package io.github.paxel.dedup;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class Duplicate {
    private final FileCollector.FileMessage original;
    private final FileCollector.FileMessage duplicate;
}
