package paxel.dedup.infrastructure.logging;

import java.util.concurrent.locks.ReentrantLock;

public class ConsoleLogger {
    private enum LogLevel {
        OUT, ERR
    }

    private static final ConsoleLogger INSTANCE = new ConsoleLogger();
    private volatile boolean verbose = false;

    private ConsoleLogger() {
    }

    public static ConsoleLogger getInstance() {
        return INSTANCE;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void info(String message, Object... args) {
        log(LogLevel.OUT, message, args);
    }

    public void warn(String message, Object... args) {
        log(LogLevel.ERR, message, args);
    }

    public void debug(String message, Object... args) {
        if (verbose) {
            log(LogLevel.OUT, message, args);
        }
    }

    public void error(String message, Object... args) {
        if (args != null && args.length > 0 && args[args.length - 1] instanceof Throwable t) {
            Object[] messageArgs = new Object[args.length - 1];
            System.arraycopy(args, 0, messageArgs, 0, args.length - 1);
            String formattedMessage = formatMessage(message, messageArgs);
            handleError(formattedMessage, t);
        } else {
            log(LogLevel.ERR, message, args);
        }
    }

    public void error(String message, Throwable t) {
        handleError(message, t);
    }

    private void handleError(String message, Throwable t) {
        String fullMessage = message;
        if (t != null) {
            String exceptionMessage = t.getMessage();
            if (exceptionMessage != null) {
                fullMessage = message + ": " + exceptionMessage;
            }
        }
        log(LogLevel.ERR, fullMessage);
        if (t != null) {
            t.printStackTrace(System.err);
        }
    }

    private final ReentrantLock lock = new ReentrantLock(true);

    private void log(LogLevel level, String message, Object... args) {
        lock.lock();
        try {
            String formattedMessage = formatMessage(message, args);
            if (level == LogLevel.ERR) {
                System.err.println(formattedMessage);
            } else {
                System.out.println(formattedMessage);
            }
        } finally {
            lock.unlock();
        }
    }

    private String formatMessage(String message, Object... args) {
        if (args == null || args.length == 0 || message == null) {
            return message;
        }

        StringBuilder sb = new StringBuilder();
        int lastPos = 0;
        int argIdx = 0;

        while (argIdx < args.length) {
            int pos = message.indexOf("{}", lastPos);
            if (pos == -1) {
                break;
            }
            sb.append(message, lastPos, pos);
            sb.append(args[argIdx++]);
            lastPos = pos + 2;
        }
        sb.append(message.substring(lastPos));
        return sb.toString();
    }
}
