package de.burger.forensics.quality;


import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Architectural guardrails to keep the hexagon clean in a single-module project.
 * Adjust package names if your structure differs.
 */
@AnalyzeClasses(packages = "de.burger.forensics",
        importOptions = {
                ImportOption.DoNotIncludeTests.class
        })
public class HexagonRulesTest {

    @ArchTest
    static final ArchRule domain_is_pure =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..application..", "..adapters..", "..infrastructure..", "..tech..");

    @ArchTest
    static final ArchRule application_not_on_adapters_or_infra =
            noClasses().that().resideInAPackage("..application..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..adapters..", "..infrastructure..");

    @ArchTest
    static final ArchRule adapters_do_not_depend_on_domain_impl_details =
            classes().that().resideInAPackage("..adapters..")
                    .should().onlyDependOnClassesThat()
                    .resideInAnyPackage(
                            "java..",
                            "jakarta..",
                            "javax..",
                            "org.gradle..",
                            "com.github.javaparser..",
                            "org.springframework..",
                            "de.burger.forensics.adapters..",
                            "de.burger.forensics.adaptersupport..",
                            "de.burger.forensics.application..",
                            "de.burger.forensics.domain..",
                            "de.burger.forensics.infrastructure..");

    @ArchTest
    static final ArchRule domain_does_not_use_tracing =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage("..infrastructure.rt..");

    @ArchTest
    static final ArchRule domain_and_application_do_not_depend_on_storage_or_joern_cli =
            noClasses().that().resideInAnyPackage("..domain..", "..application..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("java.sql..", "org.h2..", "..adapters.joern..", "..adapters.persistence.h2..");
}

