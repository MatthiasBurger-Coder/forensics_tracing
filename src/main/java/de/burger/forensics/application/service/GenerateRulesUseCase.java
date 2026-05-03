package de.burger.forensics.application.service;

import de.burger.forensics.application.AnalysisContext;
import de.burger.forensics.domain.model.Rule;
import de.burger.forensics.domain.model.RuleId;
import de.burger.forensics.domain.model.RuleIdFactory;
import de.burger.forensics.domain.model.RuleTemplate;
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
        AnalysisContext context = AnalysisContext.builder().build();
        context.addSourceRoot(root);
        Instant start = clock.now();
        log.info("Starting rule generation at " + start + " for " + root);

        List<ScanEvent> events = collectScanEvents(request);
        log.debug("Scanned " + events.size() + " events");
        events.forEach(context::addEvent);
        var validationReport = ConditionValidationSupport.validate(request, context, log, events);

        Map<String, List<ScanEvent>> byMethod = groupEventsByMethod(events);
        populateMethodContexts(context, byMethod);
        List<Rule> rules = generateRules(byMethod, request);
        List<Rule> filtered = applyBranchFilter(rules, request.minBranches());
        List<String> rendered = renderRules(filtered);

        log.debug("Finished rule generation at " + clock.now() + " with " + rendered.size() + " rules");
        context.markFinished();
        return new RuleGenerationResult(rendered, context, validationReport);
    }

    private List<ScanEvent> collectScanEvents(GenerationRequest request) {
        try (Stream<ScanEvent> stream = scanner.scan(request.root())) {
            return stream
                .filter(event -> event.language() == null || "java".equalsIgnoreCase(event.language()))
                .filter(event -> matchesPrefixes(event.location(), request.packagePrefixes()))
                .sorted(Comparator
                    .comparing((ScanEvent e) -> e.location().fqcn())
                    .thenComparing(e -> e.location().method())
                    .thenComparingInt(e -> e.location().line())
                    .thenComparing(ScanEvent::kind))
                .toList();
        }
    }

    private Map<String, List<ScanEvent>> groupEventsByMethod(List<ScanEvent> events) {
        Map<String, List<ScanEvent>> byMethod = new LinkedHashMap<>();
        for (ScanEvent event : events) {
            String key = methodKey(event);
            byMethod.computeIfAbsent(key, k -> new ArrayList<>()).add(event);
        }
        return byMethod;
    }

    private List<String> parseParameterTypes(String signature) {
        if (signature == null) {
            return List.of();
        }
        int open = signature.indexOf('(');
        int close = signature.lastIndexOf(')');
        if (open < 0 || close < open) {
            return List.of();
        }
        String params = signature.substring(open + 1, close).trim();
        if (params.isEmpty()) {
            return List.of();
        }
        String[] tokens = params.split(",");
        List<String> types = new ArrayList<>();
        for (String token : tokens) {
            String candidate = token.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            int lastSpace = candidate.lastIndexOf(' ');
            String type = lastSpace >= 0 ? candidate.substring(0, lastSpace).trim() : candidate;
            types.add(type);
        }
        return types;
    }

    private String simpleClassName(String fqcn) {
        int idx = fqcn.lastIndexOf('.');
        if (idx < 0) {
            return fqcn;
        }
        return fqcn.substring(idx + 1);
    }

    private void populateMethodContexts(AnalysisContext context, Map<String, List<ScanEvent>> byMethod) {
        for (Map.Entry<String, List<ScanEvent>> entry : byMethod.entrySet()) {
            String methodId = entry.getKey();
            List<ScanEvent> methodEvents = entry.getValue();
            if (methodEvents.isEmpty()) {
                continue;
            }
            ScanEvent first = methodEvents.get(0);
            SourceLocation location = first.location();
            context.addMethodContext(
                methodId,
                simpleClassName(location.fqcn()),
                location.method(),
                parseParameterTypes(first.signature()),
                first.returnType(),
                methodEvents
            );
        }
    }

    private List<Rule> generateRules(Map<String, List<ScanEvent>> byMethod, GenerationRequest request) {
        List<Rule> rules = new ArrayList<>();
        for (List<ScanEvent> methodEvents : byMethod.values()) {
            rules.addAll(generateRulesForMethod(methodEvents, request));
        }
        return rules;
    }

    private List<Rule> generateRulesForMethod(List<ScanEvent> methodEvents, GenerationRequest request) {
        List<Rule> rules = new ArrayList<>();
        if (methodEvents.isEmpty()) {
            return rules;
        }
        if (request.includeEntryExit()) {
            addEntryRuleIfMissing(methodEvents, request, rules);
            addEventRules(methodEvents, request, rules);
            addExitRuleIfMissing(methodEvents, request, rules);
        } else {
            addEventRules(methodEvents, request, rules);
        }
        return rules;
    }

    private void addEntryRuleIfMissing(List<ScanEvent> methodEvents,
                                       GenerationRequest request,
                                       List<Rule> rules) {
        boolean hasEntry = methodEvents.stream().anyMatch(e -> e.kind() == RuleTemplate.METHOD_ENTER);
        if (!hasEntry) {
            ScanEvent first = methodEvents.get(0);
            rules.add(entryRule(first.location(), first.signature(), request.helperFqcn()));
        }
    }

    private void addExitRuleIfMissing(List<ScanEvent> methodEvents,
                                      GenerationRequest request,
                                      List<Rule> rules) {
        boolean hasExit = methodEvents.stream().anyMatch(e -> e.kind() == RuleTemplate.METHOD_EXIT);
        boolean hasReturn = methodEvents.stream().anyMatch(e -> e.kind() == RuleTemplate.RETURN);
        if (!hasExit && !hasReturn) {
            ScanEvent last = methodEvents.get(methodEvents.size() - 1);
            rules.add(exitRule(last.location(), last.signature(), request.helperFqcn()));
        }
    }

    private void addEventRules(List<ScanEvent> methodEvents,
                               GenerationRequest request,
                               List<Rule> rules) {
        boolean returnRuleAdded = false;
        for (ScanEvent event : methodEvents) {
            if (event.kind() == RuleTemplate.RETURN && returnRuleAdded) {
                continue;
            }
            if (event.kind() == RuleTemplate.RETURN) {
                returnRuleAdded = true;
            }
            rules.addAll(mapEvent(event, request.helperFqcn(), request.safeMode()));
        }
    }

    private List<Rule> applyBranchFilter(List<Rule> rules, int minBranches) {
        if (minBranches <= 0) {
            return rules;
        }
        Map<String, List<Rule>> byMethod = new LinkedHashMap<>();
        for (Rule rule : rules) {
            String key = rule.location().fqcn() + "#" + rule.location().method();
            byMethod.computeIfAbsent(key, k -> new ArrayList<>()).add(rule);
        }
        List<Rule> filtered = new ArrayList<>();
        for (Map.Entry<String, List<Rule>> entry : byMethod.entrySet()) {
            long branchCount = entry.getValue().stream()
                .filter(rule -> rule.type() == RuleTemplate.IF_TRUE
                    || rule.type() == RuleTemplate.IF_FALSE
                    || rule.type() == RuleTemplate.SWITCH
                    || rule.type() == RuleTemplate.SWITCH_CASE)
                .count();
            if (branchCount >= minBranches) {
                filtered.addAll(entry.getValue());
            }
        }
        return filtered;
    }

    private List<String> renderRules(List<Rule> rules) {
        Set<String> rendered = new LinkedHashSet<>();
        for (Rule rule : rules) {
            rendered.add(renderer.render(rule));
        }
        return List.copyOf(rendered);
    }

    private List<Rule> mapEvent(ScanEvent event, String helperFqcn, boolean safeMode) {
        List<Rule> rules = new ArrayList<>();
        RuleTemplate type = event.kind();
        switch (type) {
            case IF_TRUE -> rules.add(ruleFrom(event, helperFqcn, safeMode, true, RuleTemplate.IF_TRUE));
            case IF_FALSE -> rules.add(ruleFrom(event, helperFqcn, safeMode, false, RuleTemplate.IF_FALSE));
            case SWITCH -> rules.add(ruleFrom(event, helperFqcn, false, true, RuleTemplate.SWITCH));
            case SWITCH_CASE -> rules.add(ruleFrom(event, helperFqcn, false, true, RuleTemplate.SWITCH_CASE));
            case RETURN -> rules.add(ruleFrom(event, helperFqcn, false, true, RuleTemplate.RETURN));
            case THROW -> rules.add(ruleFrom(event, helperFqcn, false, true, RuleTemplate.THROW));
            case METHOD_ENTER , METHOD_EXIT -> rules.add(ruleFrom(event, helperFqcn, false, true, type));
            case THREAD_LIFECYCLE, JDBC_EXECUTE -> {
                // These templates are rendered via the explicit Gradle task path, not scanner events.
            }
        }
        return rules;
    }

    private Rule ruleFrom(ScanEvent event,
                          String helperFqcn,
                          boolean safeMode,
                          boolean positive,
                          RuleTemplate overrideType) {
        ConditionStrategy base = strategyFactory.from(event.conditionText(), overrideType, event.returnType());
        String baseRendered = base.toBytemanIf();
        RuleId ruleId = RuleIdFactory.from(event, baseRendered);
        ConditionStrategy effective = safeMode
            ? SafeMode.wrap(base, helperFqcn, ruleId)
            : base;
        String renderedCondition = effective.toBytemanIf();
        return new Rule(
            ruleId,
            event.location(),
            renderedCondition,
            positive,
            helperFqcn,
            overrideType,
            event.signature(),
            event.returnType()
        );
    }

    private Rule entryRule(SourceLocation location, String methodSignature, String helperFqcn) {
        RuleId ruleId = RuleIdFactory.from(location, RuleTemplate.METHOD_ENTER);
        return new Rule(ruleId, location, "true", true, helperFqcn, RuleTemplate.METHOD_ENTER, methodSignature, null);
    }

    private Rule exitRule(SourceLocation location, String methodSignature, String helperFqcn) {
        RuleId ruleId = RuleIdFactory.from(location, RuleTemplate.METHOD_EXIT);
        return new Rule(ruleId, location, "true", true, helperFqcn, RuleTemplate.METHOD_EXIT, methodSignature, null);
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
