package com.ecole.saas;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.core.ApplicationModules;

class ArchitectureTest {

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.ecole.saas");

    @Test
    void domainLayerMustNotDependOnSpringFramework() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                .because("La couche domain doit rester du Java pur (annotations jakarta.persistence autorisées, aucune annotation ni type Spring).")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void productionClassesMustNotUseFieldInjection() {
        noFields()
                .should().beAnnotatedWith(Autowired.class)
                .because("L'injection par constructeur est obligatoire ; @Autowired sur champ est interdit.")
                .allowEmptyShould(true)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void applicationModulesMustRespectBoundaries() {
        ApplicationModules.of(EcoleSaasApplication.class).verify();
    }
}
