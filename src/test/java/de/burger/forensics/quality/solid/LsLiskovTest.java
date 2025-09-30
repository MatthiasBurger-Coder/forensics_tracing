package de.burger.forensics.quality.solid;

import static org.assertj.core.api.Assertions.assertThat;

import de.burger.forensics.quality.solid.support.ClassDependencyInspector;
import de.burger.forensics.quality.solid.support.ClasspathScanner;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LsLiskovTest {

    @Test
    void domain_types_should_not_reference_impl_suffix_types() {
        Set<Class<?>> domainClasses = ClasspathScanner.scanPackage("de.burger.forensics.domain");
        for (Class<?> type : domainClasses) {
            Set<Class<?>> dependencies = ClassDependencyInspector.collectDependencies(type);
            assertThat(dependencies)
                .withFailMessage("Domain type %s should not rely on implementation details: %s", type.getName(), dependencies)
                .allMatch(dep -> !dep.getSimpleName().endsWith("Impl"));
        }
    }
}
