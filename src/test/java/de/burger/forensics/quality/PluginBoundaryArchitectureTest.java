package de.burger.forensics.quality;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "de.burger.forensics",
        importOptions = {
                ImportOption.DoNotIncludeTests.class
        })
public class PluginBoundaryArchitectureTest {

    @ArchTest
    static final ArchRule grpc_client_does_not_depend_on_gradle_apis =
            noClasses().that().resideInAPackage("..plugin.btmgen.grpc..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("org.gradle..", "org.apache.maven..");

    @ArchTest
    static final ArchRule gradle_adapter_does_not_depend_on_maven_apis =
            noClasses().that().resideInAPackage("..plugin.btmgen.gradle..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("org.apache.maven..");

    @ArchTest
    static final ArchRule maven_adapter_does_not_depend_on_gradle_apis =
            noClasses().that().resideInAPackage("..plugin.btmgen.maven..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("org.gradle..");
}
