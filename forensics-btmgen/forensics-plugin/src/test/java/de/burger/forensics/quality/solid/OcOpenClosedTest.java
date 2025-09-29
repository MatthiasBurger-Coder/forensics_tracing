package de.burger.forensics.quality.solid;

import static org.assertj.core.api.Assertions.assertThat;

import de.burger.forensics.quality.solid.support.ClassDependencyInspector;
import de.burger.forensics.quality.solid.support.ClasspathScanner;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OcOpenClosedTest {

    @Test
    void domain_should_not_depend_on_adapters_or_plugin() {
        Set<Class<?>> domainClasses = ClasspathScanner.scanPackage("de.burger.forensics.domain");
        for (Class<?> type : domainClasses) {
            Set<Class<?>> dependencies = ClassDependencyInspector.collectDependencies(type);
            assertThat(dependencies)
                .withFailMessage("Domain type %s depends on %s", type.getName(), dependencies)
                .allMatch(dep -> !isAdapterOrPlugin(dep));
        }
    }

    @Test
    void application_should_not_depend_on_plugin() {
        Set<Class<?>> appClasses = ClasspathScanner.scanPackage("de.burger.forensics.application");
        for (Class<?> type : appClasses) {
            Set<Class<?>> dependencies = ClassDependencyInspector.collectDependencies(type);
            assertThat(dependencies)
                .withFailMessage("Application type %s depends on %s", type.getName(), dependencies)
                .allMatch(dep -> !dep.getPackageName().contains(".plugin"));
        }
    }

    private boolean isAdapterOrPlugin(Class<?> type) {
        String pkg = type.getPackageName();
        return pkg.contains(".adapters") || pkg.contains(".plugin");
    }
}
