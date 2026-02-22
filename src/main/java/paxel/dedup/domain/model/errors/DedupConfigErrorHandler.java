package paxel.dedup.domain.model.errors;

import paxel.dedup.infrastructure.logging.ConsoleLogger;

public class DedupConfigErrorHandler {
    private static final ConsoleLogger log = ConsoleLogger.getInstance();

    public void dump(DedupError error) {
        if (error == null) return;
        if (error.exception() != null) {
            log.error("{}", error.describe(), error.exception());
        } else {
            log.error("{}", error.describe());
        }
    }
}
