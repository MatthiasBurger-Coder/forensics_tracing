package de.burger.forensics.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import de.burger.forensics.domain.model.RuleType;
import de.burger.forensics.domain.model.ScanEvent;
import de.burger.forensics.domain.model.SourceLocation;
import de.burger.forensics.domain.port.out.ClockPort;
import de.burger.forensics.domain.port.out.CodeScanPort;
import de.burger.forensics.domain.port.out.LogPort;
import de.burger.forensics.domain.port.out.RuleRenderPort;
import de.burger.forensics.domain.strategy.DefaultStrategyFactory;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class GenerateRulesUseCaseTest {

    @Test
    void generatesRulesUsingPorts() {
        SourceLocation location = new SourceLocation("com.example.Test", "doWork", 42);
        ScanEvent event = new ScanEvent(location, "doWork()", RuleType.IF_TRUE, "x > 0", "java");

        CodeScanPort scanner = root -> Stream.of(event);
        RuleRenderPort renderer = rule -> "RULE " + rule.id().value();
        ClockPort clock = () -> Instant.parse("2025-01-01T00:00:00Z");
        AtomicReference<String> info = new AtomicReference<>();
        LogPort log = new LogPort() {
            @Override
            public void info(String message) {
                info.set(message);
            }

            @Override
            public void warn(String message) {
            }

            @Override
            public void debug(String message) {
            }
        };

        GenerateRulesUseCase useCase = new GenerateRulesUseCase(
            scanner,
            renderer,
            clock,
            log,
            new DefaultStrategyFactory()
        );

        GenerationRequest request = new GenerationRequest(
            Path.of("."),
            "org.example.trace.SafeEval",
            false,
            true,
            List.of(),
            0,
            List.of()
        );

        RuleGenerationResult result = useCase.generate(request);
        assertThat(result.renderedRules())
            .hasSize(1)
            .first()
            .satisfies(rule -> assertThat(rule).startsWith("RULE "));
        assertThat(info.get()).contains("Starting rule generation");
    }
}
