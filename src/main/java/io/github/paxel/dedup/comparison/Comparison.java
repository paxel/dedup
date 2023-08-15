package io.github.paxel.dedup.comparison;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString(includeFieldNames = false)
public class Comparison {

    private final String key;
}
