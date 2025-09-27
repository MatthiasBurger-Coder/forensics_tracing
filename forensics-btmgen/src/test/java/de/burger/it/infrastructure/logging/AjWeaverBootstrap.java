package de.burger.it.infrastructure.logging;

import net.bytebuddy.agent.ByteBuddyAgent;
import org.aspectj.weaver.loadtime.ClassPreProcessorAgentAdapter;

import java.lang.instrument.Instrumentation;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Installs a Java Instrumentation instance via ByteBuddy's self-attach and registers
 * the AspectJ class file transformer so @AspectJ aspects can be woven at load time
 * during tests without requiring -javaagent.
 */
public final class AjWeaverBootstrap {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);

    private AjWeaverBootstrap() {}

    public static void ensureInstalled() {
        if (INSTALLED.get()) return;
        try {
            final Instrumentation inst = ByteBuddyAgent.install();
            inst.addTransformer(new ClassPreProcessorAgentAdapter(), true);
            // Retransform already loaded classes in target packages so advice is applied even if they loaded early
            try {
                Class<?>[] loaded = inst.getAllLoadedClasses();
                java.util.List<Class<?>> targets = new java.util.ArrayList<>();
                for (Class<?> c : loaded) {
                    if (c == null) continue;
                    String name = c.getName();
                    if (name.startsWith("de.burger.forensics.") || name.startsWith("org.example.trace.")) {
                        targets.add(c);
                    }
                }
                if (inst.isRetransformClassesSupported() && !targets.isEmpty()) {
                    inst.retransformClasses(targets.toArray(new Class<?>[0]));
                }
            } catch (Throwable ignored2) {
                // ignore retransformation failures
            }
            INSTALLED.set(true);
        } catch (Throwable ignored) {
            // Never fail tests due to missing attach permissions; aspect logging is best-effort
        }
    }
}
