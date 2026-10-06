package com.riwi.skillbridge.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/*
 * Reglas de arquitectura hexagonal. ArchUnit analiza las clases compiladas.
 *  - domain: Java puro, no depende de nada más.
 *  - application: usa domain y sus puertos, nunca infrastructure.
 *  - infrastructure: puede usar todo (adaptadores).
 * Si una regla falla, el mensaje indica la clase y la dependencia prohibida.
 */
// Analiza todo el proyecto, excepto las clases de src/test.
@AnalyzeClasses(packages = "com.riwi.skillbridge", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    // Paquetes de cada capa (".." incluye los subpaquetes).
    private static final String DOMAIN = "com.riwi.skillbridge.domain..";
    private static final String APPLICATION = "com.riwi.skillbridge.application..";
    private static final String INFRASTRUCTURE = "com.riwi.skillbridge.infrastructure..";

    // Regla 1: domain no depende de application ni de infrastructure.
    @ArchTest
    static final ArchRule domain_must_not_depend_on_application_or_infrastructure =
            noClasses().that().resideInAPackage(DOMAIN)                  // ninguna clase de domain...
                    .should().dependOnClassesThat()                       // ...debe depender de...
                    .resideInAnyPackage(APPLICATION, INFRASTRUCTURE)      // ...application o infrastructure
                    .because("el dominio no debe conocer las capas externas");

    // Regla 2: domain no depende de frameworks (debe ser Java puro).
    @ArchTest
    static final ArchRule domain_must_not_depend_on_frameworks =
            noClasses().that().resideInAPackage(DOMAIN)
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework..",     // Spring
                            "jakarta.persistence..",     // JPA (@Entity, @Id...)
                            "jakarta.validation..",      // Bean Validation
                            "org.hibernate..",           // Hibernate
                            "com.fasterxml.jackson..")   // Jackson (JSON)
                    .because("el dominio debe ser Java puro, sin frameworks");

    // Regla 3: application no depende de infrastructure.
    // Debe hablar con el exterior solo a través de puertos (application.port.out).
    @ArchTest
    static final ArchRule application_must_not_depend_on_infrastructure =
            noClasses().that().resideInAPackage(APPLICATION)
                    .should().dependOnClassesThat()
                    .resideInAPackage(INFRASTRUCTURE)
                    .because("los casos de uso solo deben usar puertos, no adaptadores");
}
