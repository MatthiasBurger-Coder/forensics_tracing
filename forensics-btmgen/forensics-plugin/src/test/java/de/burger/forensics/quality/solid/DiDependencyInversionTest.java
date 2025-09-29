package de.burger.forensics.quality.solid;

import static org.assertj.core.api.Assertions.assertThat;

import de.burger.forensics.quality.solid.support.ClassDependencyInspector;
import de.burger.forensics.quality.solid.support.ClasspathScanner;
import java.lang.reflect.Modifier;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DiDependencyInversionTest {

    @Test
    void adapters_should_implement_domain_ports() {
        Set<Class<?>> adapterClasses = ClasspathScanner.scanPackage("de.burger.forensics.adapters");
        adapterClasses.addAll(ClasspathScanner.scanPackage("de.burger.forensics.plugin.adapters"));
        for (Class<?> type : adapterClasses) {
            if (isConcrete(type)) {
                boolean implementsPort = implementsDomainPort(type);
                assertThat(implementsPort)
                    .withFailMessage("Adapter %s should implement a domain port", type.getName())
                    .isTrue();
            }
        }
    }

    @Test
    void application_should_depend_on_ports_not_implementations() {
        Set<Class<?>> appClasses = ClasspathScanner.scanPackage("de.burger.forensics.application");
        for (Class<?> type : appClasses) {
            Set<Class<?>> dependencies = ClassDependencyInspector.collectDependencies(type);
            assertThat(dependencies)
                .withFailMessage("Application type %s depends on adapters: %s", type.getName(), dependencies)
                .allMatch(dep -> !dep.getPackageName().contains(".adapters"));
            dependencies.stream()
                .filter(dep -> dep.getPackageName().contains(".domain.port"))
                .forEach(dep -> assertThat(dep.isInterface())
                    .withFailMessage("Application type %s should depend on interfaces but references %s", type.getName(), dep.getName())
                    .isTrue());
        }
    }

    private boolean implementsDomainPort(Class<?> type) {
        for (Class<?> iface : type.getInterfaces()) {
            if (iface.getPackageName().startsWith("de.burger.forensics.domain.port")) {
                return true;
            }
        }
        Class<?> superclass = type.getSuperclass();
        if (superclass != null && !Object.class.equals(superclass)) {
            return implementsDomainPort(superclass);
        }
        return false;
    }

    private boolean isConcrete(Class<?> type) {
        int modifiers = type.getModifiers();
        return !Modifier.isAbstract(modifiers) && !type.isInterface();
    }
}
