package com.workshop.orders;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Keeps the boundary from the workshop honest: only the lambda.* adapters may know about AWS,
 * HTTP or JSON. Domain, application and persistence code stays ordinary Java.
 */
class ArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.workshop.orders");

    @Test
    void coreDoesNotDependOnAwsJsonOrAdapters() {
        noClasses()
                .that().resideInAnyPackage("..domain..", "..application..", "..persistence..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.amazonaws..", "com.fasterxml..", "com.workshop.orders.lambda..")
                .check(CLASSES);
    }

    @Test
    void domainDoesNotDependOnApplicationOrPersistence() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..application..", "..persistence..")
                .check(CLASSES);
    }

    @Test
    void theTwoIntegrationStylesAreIndependent() {
        noClasses().that().resideInAPackage("..lambda.direct..")
                .should().dependOnClassesThat().resideInAPackage("..lambda.proxy..")
                .check(CLASSES);
        noClasses().that().resideInAPackage("..lambda.proxy..")
                .should().dependOnClassesThat().resideInAPackage("..lambda.direct..")
                .check(CLASSES);
    }
}
