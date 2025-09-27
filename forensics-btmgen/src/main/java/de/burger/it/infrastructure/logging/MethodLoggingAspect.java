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

    /**
     * Per-target logger cache to avoid repeated lookups.
     */
    private static final Map<Class<?>, Logger> PER_CLASS_LOGGERS = new ConcurrentHashMap<>();
    private static final ThreadLocal<Long> START_NS = new ThreadLocal<>();
    private static final String CID = "cid";


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

        loggerFor(jp).info("→ {} {}", shortSig(jp), argsOf(jp));
    }

    @AfterReturning("appOps() && !@annotation(de.burger.it.infrastructure.logging.SuppressLogging)")
    public void onReturn(final JoinPoint jp) {
        final Long started = START_NS.get();
        final long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - (started != null ? started : System.nanoTime()));
        START_NS.set(0L);
        loggerFor(jp).info("← {} OK in {} ms", shortSig(jp), elapsedMs);
    }

    @AfterThrowing(pointcut = "appOps() && !@annotation(de.burger.it.infrastructure.logging.SuppressLogging)", throwing = "ex")
    public void onThrow(final JoinPoint jp, final Throwable ex) {
        final Long started = START_NS.get();
        final long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - (started != null ? started : System.nanoTime()));
        START_NS.set(0L);
        loggerFor(jp).error("✖ {} failed in {} ms: {}", shortSig(jp), elapsedMs, ex.getMessage(), ex);
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
}
