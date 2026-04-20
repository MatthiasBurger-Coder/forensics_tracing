package de.burger.forensics.infrastructure.rt;

import java.lang.reflect.Field;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

final class RtTraceLogCapture {

    private RtTraceLogCapture() {
    }

    static synchronized String capture(Runnable runnable) {
        Logger logger = traceLogger();
        Handler[] originalHandlers = logger.getHandlers();
        boolean originalUseParentHandlers = logger.getUseParentHandlers();
        Level originalLevel = logger.getLevel();
        BufferingHandler bufferingHandler = new BufferingHandler();

        try {
            for (Handler handler : originalHandlers) {
                logger.removeHandler(handler);
            }
            logger.setUseParentHandlers(false);
            logger.setLevel(Level.INFO);
            logger.addHandler(bufferingHandler);
            runnable.run();
            bufferingHandler.flush();
            return bufferingHandler.messages();
        } finally {
            logger.removeHandler(bufferingHandler);
            logger.setUseParentHandlers(originalUseParentHandlers);
            logger.setLevel(originalLevel);
            for (Handler handler : originalHandlers) {
                logger.addHandler(handler);
            }
        }
    }

    private static Logger traceLogger() {
        try {
            Field field = RtTrace.class.getDeclaredField("TRACE_LOGGER");
            field.setAccessible(true);
            return (Logger) field.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to access RtTrace logger", exception);
        }
    }

    private static final class BufferingHandler extends Handler {
        private final StringBuilder messages = new StringBuilder();

        private BufferingHandler() {
            setLevel(Level.ALL);
        }

        @Override
        public void publish(LogRecord logRecord) {
            if (isLoggable(logRecord)) {
                messages.append(logRecord.getMessage()).append(System.lineSeparator());
            }
        }

        @Override
        public void flush() {
            // No buffering beyond the in-memory StringBuilder needs explicit flushing.
        }

        @Override
        public void close() {
            // This test handler does not own external resources that need cleanup.
        }

        private String messages() {
            return messages.toString();
        }
    }
}
