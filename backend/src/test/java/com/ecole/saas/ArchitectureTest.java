package com.ecole.saas;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.web.bind.annotation.RestController;

class ArchitectureTest {

    private static final Set<String> FORBIDDEN_FLOAT_TYPES = Set.of(
            "float",
            "double",
            Float.class.getName(),
            Double.class.getName());

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.ecole.saas");

    @Test
    void domainLayerMustNotDependOnSpringFramework() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                .because(
                        "La couche domain doit rester du Java pur (annotations jakarta.persistence autorisées, aucune annotation ni type Spring).")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void domainLayerMustNotDependOnOuterLayers() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..api..", "..application..", "..infrastructure..")
                .because("La couche domain ne doit jamais dépendre des couches api, application ou infrastructure.")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void apiLayerMustNotDependOnModuleInfrastructure() {
        noClasses()
                .that().resideInAPackage("..api..")
                .should().dependOnClassesThat(
                        resideInAPackage("..infrastructure..")
                                .and(resideOutsideOfPackage("com.ecole.saas.shared.infrastructure.security..")))
                .because(
                        "La couche api ne doit pas accéder directement aux dépôts ou adaptateurs d'infrastructure métier.")
                .allowEmptyShould(true)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void applicationLayerMustNotDependOnApiLayer() {
        noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..api..")
                .because("La couche application ne doit jamais dépendre des DTO ou contrôleurs de la couche api.")
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
    void restControllersMustNotReturnJpaEntitiesDirectly() {
        methods()
                .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
                .and().arePublic()
                .should(notReturnJpaEntity())
                .because(
                        "Les contrôleurs REST doivent exposer exclusivement des DTO dédiés, jamais d'entités JPA @Entity.")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void monetaryAndGradeModulesMustNotUseFloatingPointNumbers() {
        noFields()
                .that().areDeclaredInClassesThat()
                .resideInAnyPackage("..finance..", "..grade..", "..center..", "..schooldocs..", "..schoolimport..",
                        "..reporting..")
                .should().haveRawType(float.class)
                .orShould().haveRawType(double.class)
                .orShould().haveRawType(Float.class)
                .orShould().haveRawType(Double.class)
                .because(
                        "Les montants FCFA (entiers long/BIGINT) et notes/moyennes/taux (BigDecimal) ne doivent jamais utiliser float ou double.")
                .allowEmptyShould(true)
                .check(PRODUCTION_CLASSES);

        methods()
                .that().areDeclaredInClassesThat()
                .resideInAnyPackage("..finance..", "..grade..", "..center..", "..schooldocs..", "..schoolimport..",
                        "..reporting..")
                .should(notUseFloatingPointInSignature())
                .because(
                        "Aucune méthode des modules finance, grade, center, schooldocs, schoolimport ou reporting ne doit exposer ou accepter float ou double.")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void testConfigurationMustNotBePackagedInMainResources() {
        assertThat(Files.exists(Path.of("src/main/resources/application-test.yml")))
                .as("application-test.yml ne doit jamais se trouver dans src/main/resources (risque de packaging des secrets de test).")
                .isFalse();
        assertThat(Files.exists(Path.of("src/test/resources/application-test.yml")))
                .as("application-test.yml doit résider exclusivement dans src/test/resources.")
                .isTrue();
    }

    @Test
    void applicationModulesMustRespectBoundaries() {
        ApplicationModules.of(EcoleSaasApplication.class).verify();
    }

    private static ArchCondition<JavaMethod> notReturnJpaEntity() {
        return new ArchCondition<>("ne pas retourner d'entité JPA @Entity") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (containsJpaEntity(method.getReturnType())) {
                    events.add(SimpleConditionEvent.violated(
                            method,
                            method.getFullName() + " expose directement une entité @Entity dans son type de retour : "
                                    + method.getReturnType().getName()));
                }
            }
        };
    }

    private static boolean containsJpaEntity(JavaType type) {
        JavaClass raw = type.toErasure();
        if (raw.isAnnotatedWith(Entity.class)) {
            return true;
        }
        if (type instanceof JavaParameterizedType parameterized) {
            for (JavaType arg : parameterized.getActualTypeArguments()) {
                if (containsJpaEntity(arg)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static ArchCondition<JavaMethod> notUseFloatingPointInSignature() {
        return new ArchCondition<>("ne pas utiliser float ou double dans sa signature") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (FORBIDDEN_FLOAT_TYPES.contains(method.getRawReturnType().getName())) {
                    events.add(SimpleConditionEvent.violated(
                            method,
                            method.getFullName() + " retourne un type flottant interdit : "
                                    + method.getRawReturnType().getName()));
                }
                for (JavaClass param : method.getRawParameterTypes()) {
                    if (FORBIDDEN_FLOAT_TYPES.contains(param.getName())) {
                        events.add(SimpleConditionEvent.violated(
                                method,
                                method.getFullName() + " accepte un paramètre flottant interdit : " + param.getName()));
                    }
                }
            }
        };
    }
}
