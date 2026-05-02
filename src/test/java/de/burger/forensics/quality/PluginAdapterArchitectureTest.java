package de.burger.forensics.quality;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Guards the build-tool adapter split around the shared BTM generation runner.
 */
@AnalyzeClasses(packages = "de.burger.forensics",
        importOptions = {
                ImportOption.DoNotIncludeTests.class
        })
public class PluginAdapterArchitectureTest {

    private static final String GRADLE_API = "org.gradle..";
    private static final String MAVEN_API = "org.apache.maven..";

    @ArchTest
    static final ArchRule btm_generation_common_does_not_depend_on_build_tool_apis =
            noClasses().that().resideInAPackage("..plugin.btmgen.common..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(GRADLE_API, MAVEN_API);

    @ArchTest
    static final ArchRule btm_generation_gradle_adapter_does_not_depend_on_maven =
            noClasses().that().resideInAPackage("..plugin.btmgen.gradle..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(MAVEN_API);

    @ArchTest
    static final ArchRule btm_generation_maven_adapter_does_not_depend_on_gradle =
            noClasses().that().resideInAPackage("..plugin.btmgen.maven..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(GRADLE_API);

    @ArchTest
    static final ArchRule domain_does_not_depend_on_build_tool_apis =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(GRADLE_API, MAVEN_API);

    @ArchTest
    static final ArchRule application_does_not_depend_on_build_tool_apis =
            noClasses().that().resideInAPackage("..application..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(GRADLE_API, MAVEN_API);

    @ArchTest
    static final ArchRule javaparser_adapters_do_not_depend_on_build_tool_apis =
            noClasses().that().resideInAnyPackage("..adapters.javaparser..", "..adaptersupport.javaparser..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(GRADLE_API, MAVEN_API);
}
