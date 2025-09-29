package de.burger.forensics.quality.solid;

import static org.assertj.core.api.Assertions.assertThat;

import de.burger.forensics.quality.solid.support.ClasspathScanner;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IsInterfaceSegregationTest {

    @Test
    void ports_should_be_small_and_cohesive() {
        Set<Class<?>> portClasses = ClasspathScanner.scanPackage("de.burger.forensics.domain.port");
        for (Class<?> type : portClasses) {
            if (type.isInterface()) {
                long methodCount = countDistinctMethods(type.getDeclaredMethods());
                assertThat(methodCount)
                    .withFailMessage("Interface %s is too broad with %s methods", type.getName(), methodCount)
                    .isLessThanOrEqualTo(7);
            }
        }
    }

    private long countDistinctMethods(java.lang.reflect.Method[] methods) {
        return java.util.Arrays.stream(methods)
            .map(java.lang.reflect.Method::getName)
            .distinct()
            .count();
    }
}
