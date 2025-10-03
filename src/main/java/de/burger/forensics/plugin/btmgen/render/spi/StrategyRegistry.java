package de.burger.forensics.plugin.btmgen.render.spi;

import de.burger.forensics.plugin.btmgen.render.api.RuleRenderStrategy;

import java.util.*;

public final class StrategyRegistry {
    private final Map<String, RuleRenderStrategy> map;
    private StrategyRegistry(Map<String, RuleRenderStrategy> m) { this.map = Map.copyOf(m); }

    public Optional<RuleRenderStrategy> find(String id) { return Optional.ofNullable(map.get(id)); }
    public Set<String> ids() { return map.keySet(); }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final Map<String, RuleRenderStrategy> m = new HashMap<>();
        public Builder register(RuleRenderStrategy s) { m.put(s.id(), s); return this; }
        public StrategyRegistry build() { return new StrategyRegistry(m); }
    }
}
