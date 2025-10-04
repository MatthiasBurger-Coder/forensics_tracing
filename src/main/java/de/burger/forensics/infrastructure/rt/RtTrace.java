package de.burger.forensics.infrastructure.rt;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight drop-in runtime tracing helper.
 * - No external dependencies
 * - Single JSON line per event to stdout
 * - Thread-local correlationId and nested spans
 *
 * Enable via:
 *   -Dforensics.rt.enabled=true
 * or environment:
 *   FORENSICS_RT_ENABLED=true
 */
public final class RtTrace {

    private static final boolean ENABLED = isEnabled();
    private static final AtomicLong SPAN_SEQ = new AtomicLong(1);

    private static final ThreadLocal<String> CORR_ID = ThreadLocal.withInitial(() -> null);
    private static final ThreadLocal<SpanStack> SPANS = ThreadLocal.withInitial(SpanStack::new);

    private RtTrace() {}

    /* ===== Public API ===== */

    /** Cheap global check to allow fast-path no-op when disabled. */
    public static boolean isOn() {
        return ENABLED;
    }

    /** Set or override the current correlation id for this thread. */
    public static void setCorrelationId(String correlationId) {
        if (!ENABLED) return;
        CORR_ID.set(correlationId);
    }

    /** Generate and set a fresh correlation id for this thread. */
    public static String newCorrelationId() {
        if (!ENABLED) return null;
        String id = Ids.newCorrelationId();
        CORR_ID.set(id);
        return id;
    }

    /** Get the current correlation id (may be null). */
    public static String correlationId() {
        return CORR_ID.get();
    }

    /** Begin a new span and return its token for later end. */
    public static RtSpanToken beginSpan(String name) {
        if (!ENABLED) return RtSpanToken.NOOP;
        RtSpanToken tok = new RtSpanToken(Ids.newSpanId(), name, System.nanoTime());
        SPANS.get().push(tok);
        emit(RtEvent.TIMER_START, Map.of("span", tok.spanId(), "name", name), null);
        return tok;
    }

    /** End the given span (no-op if disabled or wrong thread). */
    public static void endSpan(RtSpanToken token) {
        if (!ENABLED || token == null || token == RtSpanToken.NOOP) return;
        SPANS.get().popIfTop(token);
        long durationNanos = Math.max(0L, System.nanoTime() - token.startedAtNanos());
        emit(RtEvent.TIMER_END, Map.of(
                "span", token.spanId(),
                "name", token.name(),
                "durationNanos", Long.toString(durationNanos)
        ), null);
    }

    /** Generic trace with key/value details. */
    public static void trace(RtEvent event, Map<String, ?> details) {
        if (!ENABLED) return;
        emit(event, details, null);
    }

    /** Convenience: method enter with class/method. */
    public static void onEnter(Class<?> clazz, String method, Object... args) {
        if (!ENABLED) return;
        emit(RtEvent.METHOD_ENTER, Map.of(
                "class", safeClass(clazz),
                "method", method,
                "args", Safe.toString(args)
        ), null);
    }

    /** Convenience: method exit with optional result. */
    public static void onExit(Class<?> clazz, String method, Object result) {
        if (!ENABLED) return;
        emit(RtEvent.METHOD_EXIT, Map.of(
                "class", safeClass(clazz),
                "method", method,
                "result", Safe.toString(result)
        ), null);
    }

    /** Convenience: branch taken with label/expression outcome. */
    public static void branch(String label, Object value) {
        if (!ENABLED) return;
        emit(RtEvent.BRANCH_TAKEN, Map.of(
                "label", label,
                "value", Safe.toString(value)
        ), null);
    }

    /** Convenience: record an if/else branch hit. */
    public static void onBranch(Class<?> clazz, String method, String branch) {
        if (!ENABLED) return;
        emit(RtEvent.BRANCH_TAKEN, Map.of(
                "class", safeClass(clazz),
                "method", method,
                "kind", "if",
                "branch", branch
        ), null);
    }

    /** Convenience: record entering a switch statement. */
    public static void onSwitch(Class<?> clazz, String method, String displayName) {
        if (!ENABLED) return;
        emit(RtEvent.BRANCH_TAKEN, Map.of(
                "class", safeClass(clazz),
                "method", method,
                "kind", "switch",
                "label", Objects.toString(displayName, "")
        ), null);
    }

    /** Convenience: record which switch case matched. */
    public static void onCase(Class<?> clazz, String method, String label) {
        if (!ENABLED) return;
        emit(RtEvent.BRANCH_TAKEN, Map.of(
                "class", safeClass(clazz),
                "method", method,
                "kind", "case",
                "label", Objects.toString(label, "")
        ), null);
    }

    /** Convenience: variable set. */
    public static void varSet(String name, Object value) {
        if (!ENABLED) return;
        emit(RtEvent.VAR_SET, Map.of(
                "name", name,
                "value", Safe.toString(value)
        ), null);
    }

    /** Convenience: exception observed (do not rethrow). */
    public static void onException(Throwable t) {
        if (!ENABLED || t == null) return;
        emit(RtEvent.EXCEPTION_THROWN, Map.of(
                "type", t.getClass().getName(),
                "message", Objects.toString(t.getMessage(), ""),
                "topFrame", topFrame(t)
        ), t);
    }

    /** Threads / locks helpers. */
    public static void threadFork(String threadName) {
        if (!ENABLED) return;
        emit(RtEvent.THREAD_FORK, Map.of("thread", threadName), null);
    }

    public static void threadJoin(String threadName) {
        if (!ENABLED) return;
        emit(RtEvent.THREAD_JOIN, Map.of("thread", threadName), null);
    }

    public static void lockAcquire(String lockId) {
        if (!ENABLED) return;
        emit(RtEvent.LOCK_ACQUIRE, Map.of("lock", lockId), null);
    }

    public static void lockRelease(String lockId) {
        if (!ENABLED) return;
        emit(RtEvent.LOCK_RELEASE, Map.of("lock", lockId), null);
    }

    /** IO helpers. */
    public static void ioBegin(String op, String target) {
        if (!ENABLED) return;
        emit(RtEvent.IO_BEGIN, Map.of("op", op, "target", target), null);
    }

    public static void ioEnd(String op, String target) {
        if (!ENABLED) return;
        emit(RtEvent.IO_END, Map.of("op", op, "target", target), null);
    }

    /** Custom event with arbitrary payload. */
    public static void custom(String name, Map<String, ?> payload) {
        if (!ENABLED) return;
        emit(RtEvent.CUSTOM, Map.of("name", name, "payload", Safe.toString(payload)), null);
    }

    /* ===== Internal ===== */

    private static void emit(RtEvent event, Map<String, ?> details, Throwable t) {
        // Compose a single JSON line without external libs
        StringBuilder sb = new StringBuilder(256);
        String corr = CORR_ID.get();
        RtSpanToken top = SPANS.get().peek();

        sb.append('{');
        kv(sb, "@ts", Instant.now().toString()); sb.append(',');
        kv(sb, "event", event.name()); sb.append(',');
        kv(sb, "thread", Thread.currentThread().getName()); sb.append(',');

        if (corr != null) { kv(sb, "correlationId", corr); sb.append(','); }
        if (top != null && event != RtEvent.TIMER_START) { // TIMER_START already sets span explicitly
            kv(sb, "span", top.spanId()); sb.append(',');
        }

        // details
        if (details != null && !details.isEmpty()) {
            sb.append("\"details\":{");
            boolean first = true;
            for (Map.Entry<String, ?> e : details.entrySet()) {
                if (!first) sb.append(',');
                kv(sb, e.getKey(), Safe.toString(e.getValue()));
                first = false;
            }
            sb.append('}');
        } else {
            kv(sb, "details", "");
        }

        // optional error
        if (t != null) {
            sb.append(',');
            kv(sb, "error", t.getClass().getName());
            sb.append(',');
            kv(sb, "errorMsg", Objects.toString(t.getMessage(), ""));
        }

        sb.append('}');
        System.out.println(sb.toString());
    }

    private static void kv(StringBuilder sb, String k, String v) {
        sb.append('"').append(escape(k)).append('"').append(':')
                .append('"').append(escape(v)).append('"');
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default   -> out.append(c);
            }
        }
        return out.toString();
    }

    private static String safeClass(Class<?> c) {
        return c == null ? "" : c.getName();
    }

    private static String topFrame(Throwable t) {
        var st = t.getStackTrace();
        return st.length == 0 ? "" : st[0].toString();
    }

    private static boolean isEnabled() {
        String p = System.getProperty("forensics.rt.enabled");
        if (p != null) return Boolean.parseBoolean(p);
        String env = System.getenv("FORENSICS_RT_ENABLED");
        return env != null && env.equalsIgnoreCase("true");
    }

    /* ===== Helpers ===== */

    private static final class Ids {
        static String newCorrelationId() {
            // Simple, sortable-ish id; replace with UUID if you prefer
            return "corr-" + Long.toUnsignedString(System.currentTimeMillis(), 36)
                    + "-" + Long.toUnsignedString(Thread.currentThread().threadId(), 36);
        }
        static String newSpanId() {
            return "s-" + Long.toUnsignedString(SPAN_SEQ.getAndIncrement(), 36);
        }
    }

    /** Simple LIFO stack for spans to avoid heavy Deque allocation per thread. */
    private static final class SpanStack {
        private Node head;
        void push(RtSpanToken t) { head = new Node(t, head); }
        RtSpanToken peek() { return head == null ? null : head.val; }
        void popIfTop(RtSpanToken t) { if (head != null && head.val == t) head = head.next; }
        private record Node(RtSpanToken val, Node next) {}
    }

    /** String safety to avoid accidental heavy toString()s or nulls. */
    private static final class Safe {
        static String toString(Object o) {
            try { return Objects.toString(o, ""); }
            catch (Throwable ignored) { return "<unprintable>"; }
        }
        static String toString(Object[] arr) {
            if (arr == null) return "";
            StringBuilder sb = new StringBuilder(32 + arr.length * 8);
            sb.append('[');
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) sb.append(',');
                sb.append('"').append(escape(Objects.toString(arr[i], ""))).append('"');
            }
            sb.append(']');
            return sb.toString();
        }
    }
}

