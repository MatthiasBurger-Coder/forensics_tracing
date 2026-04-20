package de.burger.forensics.quality.solid;

import static org.assertj.core.api.Assertions.assertThat;

import de.burger.forensics.quality.solid.support.ClassDependencyInspector;
import de.burger.forensics.quality.solid.support.ClasspathScanner;
import java.lang.reflect.Modifier;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SrSingleResponsibilityTest {

    @Test
    void public_domain_and_application_classes_have_limited_dependencies() {
        Set<Class<?>> domainClasses = ClasspathScanner.scanPackage("de.burger.forensics.domain");
        Set<Class<?>> appClasses = ClasspathScanner.scanPackage("de.burger.forensics.application");

        domainClasses.stream()
            .filter(this::isPublicConcreteType)
            .forEach(this::assertDependencyLimit);
        appClasses.stream()
            .filter(this::isPublicConcreteType)
            .forEach(this::assertDependencyLimit);
    }

    private boolean isPublicConcreteType(Class<?> type) {
        int modifiers = type.getModifiers();
        return Modifier.isPublic(modifiers)
            && !Modifier.isAbstract(modifiers)
            && !type.isEnum()
            && !type.isAnnotation()
            && !type.isInterface();
    }

    private void assertDependencyLimit(Class<?> type) {
        Set<Class<?>> dependencies = ClassDependencyInspector.collectDependencies(type);
        assertThat(dependencies)
            .withFailMessage("Class %s depends on %s types: %s", type.getName(), dependencies.size(), dependencies)
            .hasSizeLessThanOrEqualTo(12);
    }
}
