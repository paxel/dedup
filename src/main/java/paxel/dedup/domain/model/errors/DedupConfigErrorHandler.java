package paxel.dedup.domain.model.errors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DedupConfigErrorHandler {

    public void dump(DedupError error) {
        if (error == null) return;
        if (error.exception() != null) {
            log.error("{}", error.describe(), error.exception());
        } else {
            log.error("{}", error.describe());
        }
    }
}
