package de.burger.forensics.application.service;

import de.burger.forensics.domain.model.Rule;
import de.burger.forensics.domain.model.RuleId;
import de.burger.forensics.domain.model.RuleIdFactory;
import de.burger.forensics.domain.model.RuleType;
import de.burger.forensics.domain.model.ScanEvent;
import de.burger.forensics.domain.model.SourceLocation;
import de.burger.forensics.domain.port.out.ClockPort;
import de.burger.forensics.domain.port.out.CodeScanPort;
import de.burger.forensics.domain.port.out.LogPort;
import de.burger.forensics.domain.port.out.RuleRenderPort;
import de.burger.forensics.domain.strategy.ConditionStrategy;
import de.burger.forensics.domain.strategy.SafeMode;
import de.burger.forensics.domain.strategy.StrategyFactory;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Application service that orchestrates scanners and renderers.
 */
public final class GenerateRulesUseCase {

    private final CodeScanPort scanner;
    private final RuleRenderPort renderer;
    private final ClockPort clock;
    private final LogPort log;
    private final StrategyFactory strategyFactory;

    public GenerateRulesUseCase(CodeScanPort scanner,
                                RuleRenderPort renderer,
                                ClockPort clock,
                                LogPort log,
                                StrategyFactory strategyFactory) {
        this.scanner = Objects.requireNonNull(scanner, "scanner");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.log = Objects.requireNonNull(log, "log");
        this.strategyFactory = Objects.requireNonNull(strategyFactory, "strategyFactory");
    }

    public RuleGenerationResult generate(GenerationRequest request) {
        Objects.requireNonNull(request, "request");
        Path root = request.root();
        Instant start = clock.now();
        log.info("Starting rule generation at " + start + " for " + root);

        List<ScanEvent> events;
        try (Stream<ScanEvent> stream = scanner.scan(root)) {
            events = stream
                .filter(event -> event.language() == null || "java".equalsIgnoreCase(event.language()))
                .filter(event -> matchesPrefixes(event.location(), request.packagePrefixes()))
                .sorted(Comparator
                    .comparing((ScanEvent e) -> e.location().fqcn())
                    .thenComparing(e -> e.location().method())
                    .thenComparingInt(e -> e.location().line())
                    .thenComparing(ScanEvent::kind))
                .toList();
        }

        log.debug("Scanned " + events.size() + " events");

        List<Rule> rules = new ArrayList<>();
        Set<String> methodsWithEntryExit = new LinkedHashSet<>();

        for (ScanEvent event : events) {
            String methodKey = methodKey(event);
            if (request.includeEntryExit() && methodsWithEntryExit.add(methodKey)) {
                rules.add(entryRule(event.location(), request.helperFqcn()));
                rules.add(exitRule(event.location(), request.helperFqcn()));
            }
            rules.addAll(mapEvent(event, request.helperFqcn(), request.safeMode()));
        }

        if (request.minBranches() > 0) {
            rules = filterByBranchDensity(rules, request.minBranches());
        }

        List<String> rendered = rules.stream()
            .map(renderer::render)
            .toList();

        log.info("Finished rule generation at " + clock.now() + " with " + rendered.size() + " rules");
        return new RuleGenerationResult(rendered);
    }

    private List<Rule> mapEvent(ScanEvent event, String helperFqcn, boolean safeMode) {
        List<Rule> rules = new ArrayList<>();
        RuleType type = event.kind();
        switch (type) {
            case IF_TRUE -> rules.add(ruleFrom(event, helperFqcn, safeMode, true, RuleType.IF_TRUE));
            case IF_FALSE -> rules.add(ruleFrom(event, helperFqcn, safeMode, false, RuleType.IF_FALSE));
            case SWITCH -> rules.add(ruleFrom(event, helperFqcn, false, true, RuleType.SWITCH));
            case SWITCH_CASE -> rules.add(ruleFrom(event, helperFqcn, false, true, RuleType.SWITCH_CASE));
            case RETURN -> rules.add(ruleFrom(event, helperFqcn, false, true, RuleType.RETURN));
            case THROW -> rules.add(ruleFrom(event, helperFqcn, false, true, RuleType.THROW));
            case ENTRY, EXIT -> rules.add(ruleFrom(event, helperFqcn, false, true, type));
        }
        return rules;
    }

    private Rule ruleFrom(ScanEvent event,
                          String helperFqcn,
                          boolean safeMode,
                          boolean positive,
                          RuleType overrideType) {
        ConditionStrategy base = strategyFactory.from(event.conditionText());
        String baseRendered = base.toBytemanIf();
        RuleId ruleId = RuleIdFactory.from(event, baseRendered);
        ConditionStrategy effective = safeMode
            ? SafeMode.wrap(base, helperFqcn, ruleId)
            : base;
        String renderedCondition = effective.toBytemanIf();
        return new Rule(ruleId, event.location(), renderedCondition, positive, helperFqcn, overrideType);
    }

    private Rule entryRule(SourceLocation location, String helperFqcn) {
        RuleId ruleId = RuleIdFactory.from(location, RuleType.ENTRY);
        return new Rule(ruleId, location, "true", true, helperFqcn, RuleType.ENTRY);
    }

    private Rule exitRule(SourceLocation location, String helperFqcn) {
        RuleId ruleId = RuleIdFactory.from(location, RuleType.EXIT);
        return new Rule(ruleId, location, "true", true, helperFqcn, RuleType.EXIT);
    }

    private List<Rule> filterByBranchDensity(List<Rule> rules, int minBranches) {
        Map<String, List<Rule>> byMethod = new LinkedHashMap<>();
        for (Rule rule : rules) {
            String key = rule.location().fqcn() + "#" + rule.location().method();
            byMethod.computeIfAbsent(key, k -> new ArrayList<>()).add(rule);
        }
        List<Rule> filtered = new ArrayList<>();
        for (Map.Entry<String, List<Rule>> entry : byMethod.entrySet()) {
            long branchCount = entry.getValue().stream()
                .filter(rule -> rule.type() == RuleType.IF_TRUE
                    || rule.type() == RuleType.IF_FALSE
                    || rule.type() == RuleType.SWITCH
                    || rule.type() == RuleType.SWITCH_CASE)
                .count();
            if (branchCount >= minBranches) {
                filtered.addAll(entry.getValue());
            }
        }
        return filtered;
    }

    private boolean matchesPrefixes(SourceLocation location, List<String> prefixes) {
        if (prefixes == null || prefixes.isEmpty()) {
            return true;
        }
        String fqcn = location.fqcn();
        return prefixes.stream().anyMatch(fqcn::startsWith);
    }

    private String methodKey(ScanEvent event) {
        return methodKey(event.location(), event.signature());
    }

    private String methodKey(SourceLocation location, String signature) {
        return location.fqcn() + "#" + location.method() + "::" + Objects.requireNonNullElse(signature, "");
    }
}
