package de.burger.forensics.plugin.btmgen.maven;

import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class MavenLogAdapterTest {

    @Test
    void forwardsMessagesToMavenLog() {
        RecordingLog log = new RecordingLog();
        MavenLogAdapter adapter = new MavenLogAdapter(log);

        assertDoesNotThrow(() -> {
            adapter.info("info");
            adapter.warn("warn");
            adapter.error("error");
            adapter.debug("debug");
        });

        assertThat(log.messages).containsExactly("info:info", "warn:warn", "error:error", "debug:debug");
    }

    private static final class RecordingLog implements Log {
        private final List<String> messages = new ArrayList<>();

        @Override
        public boolean isDebugEnabled() {
            return true;
        }

        @Override
        public void debug(CharSequence content) {
            messages.add("debug:" + content);
        }

        @Override
        public void debug(CharSequence content, Throwable error) {
            debug(content);
        }

        @Override
        public void debug(Throwable error) {
            messages.add("debug:" + error.getClass().getSimpleName());
        }

        @Override
        public boolean isInfoEnabled() {
            return true;
        }

        @Override
        public void info(CharSequence content) {
            messages.add("info:" + content);
        }

        @Override
        public void info(CharSequence content, Throwable error) {
            info(content);
        }

        @Override
        public void info(Throwable error) {
            messages.add("info:" + error.getClass().getSimpleName());
        }

        @Override
        public boolean isWarnEnabled() {
            return true;
        }

        @Override
        public void warn(CharSequence content) {
            messages.add("warn:" + content);
        }

        @Override
        public void warn(CharSequence content, Throwable error) {
            warn(content);
        }

        @Override
        public void warn(Throwable error) {
            messages.add("warn:" + error.getClass().getSimpleName());
        }

        @Override
        public boolean isErrorEnabled() {
            return true;
        }

        @Override
        public void error(CharSequence content) {
            messages.add("error:" + content);
        }

        @Override
        public void error(CharSequence content, Throwable error) {
            error(content);
        }

        @Override
        public void error(Throwable error) {
            messages.add("error:" + error.getClass().getSimpleName());
        }
    }
}
