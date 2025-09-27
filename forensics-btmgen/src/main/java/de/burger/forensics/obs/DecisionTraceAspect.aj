package de.burger.forensics.obs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Logs call/return/throw events with a correlation id (cid) using SLF4J.
 * Enable via LTW (-javaagent:aspectjweaver.jar) or CTW (ajc).
 */
public aspect DecisionTraceAspect {
    pointcut appMethods():
        execution(* de.burger..*(..));

    private static final Logger LOG = LoggerFactory.getLogger("decision-trace");

    private void ensureCid() {
        try {
            String cid = MDC.get("cid");
            if (cid == null || cid.isEmpty()) {
                MDC.put("cid", java.util.UUID.randomUUID().toString());
            }
        } catch (Throwable ignore) { /* never break app */ }
    }

    before(): appMethods() {
        ensureCid();
        try {
            org.aspectj.lang.JoinPoint jp = thisJoinPoint;
            org.aspectj.lang.Signature sig = jp.getSignature();
            org.aspectj.lang.SourceLocation loc = thisJoinPointStaticPart.getSourceLocation();
            if (LOG.isInfoEnabled()) {
                LOG.info("CALL cid={} method={} args={} at={}:{}",
                    MDC.get("cid"), sig.toLongString(), java.util.Arrays.toString(jp.getArgs()),
                    safe(loc.getFileName()), Integer.valueOf(loc.getLine()));
            }
        } catch (Throwable t) { /* swallow */ }
    }

    after() returning(Object ret): appMethods() {
        try {
            org.aspectj.lang.Signature sig = thisJoinPoint.getSignature();
            if (LOG.isInfoEnabled()) {
                LOG.info("RET  cid={} method={} result={}",
                    MDC.get("cid"), sig.toLongString(), preview(ret));
            }
        } catch (Throwable t) { /* swallow */ }
    }

    after() throwing(Throwable ex): appMethods() {
        try {
            org.aspectj.lang.Signature sig = thisJoinPoint.getSignature();
            LOG.warn("THROW cid={} method={} ex={} msg={}",
                MDC.get("cid"), sig.toLongString(),
                ex.getClass().getName(), safe(ex.getMessage()));
        } catch (Throwable t) { /* swallow */ }
    }

    private static String preview(Object o) {
        if (o == null) return "null";
        String s;
        try { s = String.valueOf(o); } catch (Throwable e) { s = o.getClass().getName(); }
        if (s.length() > 300) s = s.substring(0, 300) + "…";
        return s.replace("\n","\\n").replace("\r","\\r");
    }

    private static String safe(String s) {
        if (s == null) return "";
        return s.replace("\n","\\n").replace("\r","\\r");
    }
}
