package de.burger.forensics.application.service;

import de.burger.forensics.domain.model.Rule;
import de.burger.forensics.domain.model.RuleType;
import de.burger.forensics.domain.model.ScanEvent;
import de.burger.forensics.domain.model.SourceLocation;
import de.burger.forensics.domain.port.out.ClockPort;
import de.burger.forensics.domain.port.out.CodeScanPort;
import de.burger.forensics.domain.port.out.LogPort;
import de.burger.forensics.domain.port.out.RuleRenderPort;
import de.burger.forensics.domain.strategy.StrategyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class GenerateRulesUseCaseTest {

    private final StubScanner scanner = new StubScanner();
    private final CollectingRenderer renderer = new CollectingRenderer();
    private final SequencingClock clock = new SequencingClock();
    private final RecordingLog log = new RecordingLog();
    private final StrategyFactory strategies = expression -> () -> expression == null ? "true" : expression;

    private GenerateRulesUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GenerateRulesUseCase(scanner, renderer, clock, log, strategies);
    }

    @Test
    void addsEntryExitRulesAndWrapsConditionsInSafeMode() {
        SourceLocation location = new SourceLocation("com.example.Demo", "run", 12);
        scanner.setEvents(List.of(new ScanEvent(location, "run()", RuleType.IF_TRUE, "flag", "java")));

        GenerationRequest request = new GenerationRequest(
            Path.of("src"),
            "org.example.trace.SafeEval",
            true,
            true,
            List.of(),
            0,
            List.of()
        );

        RuleGenerationResult result = useCase.generate(request);

        assertThat(result.renderedRules()).hasSize(3);
        assertThat(renderer.rendered())
            .extracting(Rule::type)
            .containsExactlyInAnyOrder(RuleType.ENTRY, RuleType.IF_TRUE, RuleType.EXIT);
        Rule ifRule = renderer.rendered().stream()
            .filter(rule -> rule.type() == RuleType.IF_TRUE)
            .findFirst()
            .orElseThrow();
        assertThat(ifRule.condition()).contains("SafeEval.eval").contains("flag");
        assertThat(log.messages()).hasSize(3);
    }

    @Test
    void filtersMethodsByPrefixesAndMinimumBranchCount() {
        SourceLocation include = new SourceLocation("com.target.Demo", "calc", 20);
        SourceLocation exclude = new SourceLocation("com.other.Other", "skip", 10);
        scanner.setEvents(List.of(
            new ScanEvent(include, "calc()", RuleType.IF_TRUE, "flag", "java"),
            new ScanEvent(include, "calc()", RuleType.IF_FALSE, "flag", "java"),
            new ScanEvent(exclude, "skip()", RuleType.IF_TRUE, "flag", "java")
        ));

        GenerationRequest request = new GenerationRequest(
            Path.of("src"),
            "helper",
            false,
            false,
            List.of("com.target"),
            2,
            List.of()
        );

        RuleGenerationResult result = useCase.generate(request);

        assertThat(result.renderedRules()).hasSize(2);
        assertThat(renderer.rendered()).allSatisfy(rule ->
            assertThat(rule.location().fqcn()).isEqualTo("com.target.Demo")
        );
    }

    @Test
    void mapsAllEventTypesToRules() {
        SourceLocation location = new SourceLocation("com.example.Full", "everything", 5);
        scanner.setEvents(List.of(
            new ScanEvent(location, "everything()", RuleType.SWITCH, "selector", "java"),
            new ScanEvent(location, "everything()", RuleType.SWITCH_CASE, "case1", "java"),
            new ScanEvent(location, "everything()", RuleType.RETURN, "return 1", "java"),
            new ScanEvent(location, "everything()", RuleType.THROW, "new IllegalStateException()", "java"),
            new ScanEvent(location, "everything()", RuleType.ENTRY, "true", "java"),
            new ScanEvent(location, "everything()", RuleType.EXIT, "true", "java"),
            new ScanEvent(location, "everything()", RuleType.IF_TRUE, "value > 0", "java"),
            new ScanEvent(location, "everything()", RuleType.IF_FALSE, "value > 0", "java"),
            new ScanEvent(location, "everything()", RuleType.SWITCH_CASE, "case1", "java")
        ));

        GenerationRequest request = new GenerationRequest(
            Path.of("src"),
            "helper",
            false,
            false,
            List.of(),
            0,
            List.of()
        );

        RuleGenerationResult result = useCase.generate(request);

        assertThat(result.renderedRules())
            .contains(
                "SWITCH:selector",
                "SWITCH_CASE:case1",
                "RETURN:return 1",
                "THROW:new IllegalStateException()",
                "ENTRY:true",
                "EXIT:true",
                "IF_TRUE:value > 0",
                "IF_FALSE:value > 0"
            );
        assertThat(renderer.rendered())
            .extracting(Rule::type)
            .contains(
                RuleType.SWITCH,
                RuleType.SWITCH_CASE,
                RuleType.RETURN,
                RuleType.THROW,
                RuleType.ENTRY,
                RuleType.EXIT,
                RuleType.IF_TRUE,
                RuleType.IF_FALSE
            );
    }

    private static final class StubScanner implements CodeScanPort {
        private List<ScanEvent> events = List.of();

        void setEvents(List<ScanEvent> events) {
            this.events = events;
        }

        @Override
        public Stream<ScanEvent> scan(Path root) {
            return events.stream();
        }
    }

    private static final class CollectingRenderer implements RuleRenderPort {
        private final List<Rule> rendered = new ArrayList<>();

        @Override
        public String render(Rule rule) {
            rendered.add(rule);
            return rule.type() + ":" + rule.condition();
        }

        List<Rule> rendered() {
            return List.copyOf(rendered);
        }
    }

    private static final class SequencingClock implements ClockPort {
        private final AtomicInteger calls = new AtomicInteger();
        private final Instant instant = Instant.parse("2024-01-01T00:00:00Z");

        @Override
        public Instant now() {
            return instant.plusSeconds(calls.getAndIncrement());
        }
    }

    private static final class RecordingLog implements LogPort {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void info(String message) {
            messages.add(message);
        }

        @Override
        public void warn(String message) {
            messages.add(message);
        }

        @Override
        public void debug(String message) {
            messages.add(message);
        }

        List<String> messages() {
            return List.copyOf(messages);
        }
    }
}
