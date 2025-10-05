package paxel.dedup.repo.index;

import java.io.IOException;

public class TunneledIoException extends RuntimeException {
    public TunneledIoException(IOException cause) {
        super(cause);
    }

    public TunneledIoException(String message, IOException cause) {
        super(message, cause);
    }

    public TunneledIoException(String message, IOException cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    @Override
    public synchronized IOException getCause() {
        return (IOException) super.getCause();
    }
}
