package de.burger.forensics.plugin.btmgen.render.spi;

import de.burger.forensics.plugin.btmgen.render.impl.*;

import de.burger.forensics.plugin.btmgen.render.impl.IfTrueRuleStrategy;
import de.burger.forensics.plugin.btmgen.render.impl.IfFalseRuleStrategy;
import de.burger.forensics.plugin.btmgen.render.impl.SwitchRuleStrategy;
import de.burger.forensics.plugin.btmgen.render.impl.SwitchCaseRuleStrategy;

/** Factory for commonly used strategy registries. */
public final class StrategyRegistries {
    private StrategyRegistries() {}

    /** Default registry with the built-in strategies and configuration-cache-safe defaults. */
    public static StrategyRegistry defaultRegistry() {
        return StrategyRegistry.builder()
                .register(new ReturnRuleStrategy())
                .register(new ThrowRuleStrategy())
                .register(new MethodEnterRuleStrategy())
                .register(new MethodExitRuleStrategy())
                .register(new ThreadLifecycleRuleStrategy())
                .register(new JdbcExecuteRuleStrategy())
                .register(new IfTrueRuleStrategy())
                .register(new IfFalseRuleStrategy())
                .register(new SwitchRuleStrategy())
                .register(new SwitchCaseRuleStrategy())
                .build();
    }
}
