package io.github.paxel.dedup;

import lombok.*;

@Getter
@AllArgsConstructor
@ToString
@EqualsAndHashCode
@Builder(setterPrefix = "set", toBuilder = true)
public class Stats {
    private final int readonly;
    private final int readwrite;
    private final int deletedSuccessfully;
    private final int deletionFailed;
    private final Throwable lastException;
}
