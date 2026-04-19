package de.burger.forensics.domain.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MethodScanContextTest {

    @Test
    void builderDefaultsAndMutationHelpersWork() {
        MethodScanContext context = MethodScanContext.builder()
                .methodId("com.example.Foo#work()")
                .className("Foo")
                .methodName("work")
                .returnType("boolean")
                .build();
        ScanEvent event = new ScanEvent(
                new SourceLocation("com.example.Foo", "work", 15),
                "work()",
                RuleTemplate.IF_TRUE,
                "flag",
                "java",
                "boolean"
        );

        context.addEvent(event);
        context.putAttribute("key", "value");

        assertThat(context.getMethodId()).isEqualTo("com.example.Foo#work()");
        assertThat(context.getClassName()).isEqualTo("Foo");
        assertThat(context.getMethodName()).isEqualTo("work");
        assertThat(context.getReturnType()).isEqualTo("boolean");
        assertThat(context.getLineStart()).isEqualTo(-1);
        assertThat(context.getLineEnd()).isEqualTo(-1);
        assertThat(context.getParameterTypes()).isEmpty();
        assertThat(context.getEvents()).containsExactly(event);
        assertThat(context.getAttributes()).isEqualTo(Map.of("key", "value"));
    }
}
