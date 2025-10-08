package paxel.dedup.repo.domain;

import paxel.dedup.model.errors.CreateConfigError;

import java.io.IOException;

public class DedupConfigErrorHandler {

    public void dump(CreateConfigError error) {
        IOException ioException = error.ioException();
        if (ioException != null) {
            System.err.println(error.path() + " not a valid config relativePath");
            ioException.printStackTrace();
        }
    }
}
