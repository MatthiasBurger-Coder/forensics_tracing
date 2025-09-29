package de.burger.it.infrastructure.logging;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Logs entry/exit and errors under the target class logger (not the aspect logger).
 * Note: AspectJ weaving is intentionally not enabled in this Gradle plugin module. The annotations
 * are kept for consumers that may enable weaving in their own builds. We suppress the IDE inspection
 * to avoid a noisy "@AspectJ support isn't enabled" warning in this module.
 */
@SuppressWarnings("AspectJ") // suppress IDE warning; weaving is opt-in for consumers
@Aspect
public class MethodLoggingAspect {

    // Provide code-style aspect accessors to satisfy LTW expectations in test runtime
    private static final MethodLoggingAspect INSTANCE = new MethodLoggingAspect();
    public static MethodLoggingAspect aspectOf() { return INSTANCE; }
    public static boolean hasAspect() { return true; }

    /**
     * Per-target logger cache to avoid repeated lookups.
     */
    private static final Map<Class<?>, Logger> PER_CLASS_LOGGERS = new ConcurrentHashMap<>();
    private static final ThreadLocal<Long> START_NS = new ThreadLocal<>();
    private static final String CID = "cid";

    // File logging mirror independent of SLF4J provider
    private static final String LOG_FILE_PROP = "forensics.btmgen.logFile";
    private static final String LOG_TO_FILE_PROP = "forensics.btmgen.logToFile";
    private static final String DEFAULT_LOG_FILE = "logs/forensics-btmgen.log";


    @Pointcut(
            "execution(public * de.burger.forensics..*(..)) || " +
                    "execution(public * org.example.trace..*(..))"
    )
    public void appOps() {}


    @Before("appOps() && !@annotation(de.burger.it.infrastructure.logging.SuppressLogging)")
    public void onEnter(final JoinPoint jp) {
        MDC.put(CID, java.util.Optional.ofNullable(MDC.get(CID))
                .filter(s -> !s.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString()));

        START_NS.set(System.nanoTime());

        final String msg = String.format("→ %s %s", shortSig(jp), argsOf(jp));
        loggerFor(jp).warn(msg); // WARN to be visible in Gradle console without --info
        fileLog("WARN", msg);
    }

    @AfterReturning("appOps() && !@annotation(de.burger.it.infrastructure.logging.SuppressLogging)")
    public void onReturn(final JoinPoint jp) {
        final Long started = START_NS.get();
        final long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - (started != null ? started : System.nanoTime()));
        START_NS.set(0L);
        final String msg = String.format("← %s OK in %d ms", shortSig(jp), elapsedMs);
        loggerFor(jp).warn(msg); // WARN to be visible in Gradle console without --info
        fileLog("WARN", msg);
    }

    @AfterThrowing(pointcut = "appOps() && !@annotation(de.burger.it.infrastructure.logging.SuppressLogging)", throwing = "ex")
    public void onThrow(final JoinPoint jp, final Throwable ex) {
        final Long started = START_NS.get();
        final long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - (started != null ? started : System.nanoTime()));
        START_NS.set(0L);
        final String msg = String.format("✖ %s failed in %d ms: %s", shortSig(jp), elapsedMs, ex.getMessage());
        loggerFor(jp).error(msg, ex);
        fileLog("ERROR", msg + " (see stacktrace in console)");
    }

    // ---- helpers (no if-statements) ----

    /**
     * Resolve the logger for the declaring type of the advised method and cache it.
     */
    private Logger loggerFor(JoinPoint jp) {
        final Class<?> type = jp.getSignature().getDeclaringType();
        return PER_CLASS_LOGGERS.computeIfAbsent(type, LoggerFactory::getLogger);
    }

    /**
     * Short signature like 'MyService.doWork(..)'.
     */
    private String shortSig(JoinPoint jp) {
        return jp.getSignature().toShortString();
    }

    /**
     * Render arguments safely.
     */
    private String argsOf(JoinPoint jp) {
        return Arrays.stream(jp.getArgs())
                .map(this::safeToString)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private String safeToString(Object o) {
        return java.util.Optional.ofNullable(o).map(Objects::toString).orElse("null");
    }

    private void fileLog(String level, String message) {
        try {
            final String enabledProp = System.getProperty(LOG_TO_FILE_PROP, "true");
            if (!Boolean.parseBoolean(enabledProp)) return;
            final String relative = System.getProperty(LOG_FILE_PROP, DEFAULT_LOG_FILE);
            final java.io.File file = new java.io.File(relative);
            final java.io.File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                // noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            final String ts = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                    .format(java.time.LocalDateTime.now());
            final String cid = java.util.Optional.ofNullable(MDC.get(CID)).orElse("-");
            final String line = ts + " [" + level + "] [cid=" + cid + "] " + message + "\n";
            try (java.io.FileWriter fw = new java.io.FileWriter(file, true)) {
                fw.write(line);
            }
        } catch (Throwable ignored) {
            // never fail application due to file logging
        }
    }
}
