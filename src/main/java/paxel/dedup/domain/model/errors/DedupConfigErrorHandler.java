package paxel.dedup.domain.model.errors;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class DedupConfigErrorHandler {

    public void dump(CreateConfigError error) {
        IOException ioException = error.ioException();
        if (ioException != null) {
            log.error("{} not a valid config path", error.path(), ioException);
        }
    }
}
