package com.supportdesk.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.supportdesk.ticket.domain.Ticket;
import com.supportdesk.ticket.domain.TicketExceptions.InvalidStatusTransitionException;
import com.supportdesk.ticket.domain.TicketStatus;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Machine-checked architecture rules (rules/java-springboot.md §2; spec/architecture.md §5.2; spec/state-machine.md
 * §5 E3/E5; plan STEP-10/26). Production classes only (review SR-32).
 */
@AnalyzeClasses(packages = "com.supportdesk", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule layers = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Api").definedBy("..ticket.api..")
            .layer("Application").definedBy("..ticket.application..")
            .layer("Domain").definedBy("..ticket.domain..")
            .layer("Persistence").definedBy("..ticket.persistence..")
            .whereLayer("Api").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Api")
            .whereLayer("Persistence").mayOnlyBeAccessedByLayers("Application")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Api", "Application", "Persistence");

    @ArchTest
    static final ArchRule domainIsFreeOfWebAndSpringServices = noClasses()
            .that().resideInAPackage("..ticket.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..", "jakarta.servlet..",
                    "org.springframework.stereotype..", "org.springframework.transaction..");

    @ArchTest
    static final ArchRule entitiesDoNotLeakIntoTheApi = noClasses()
            .that().resideInAPackage("..ticket.api..")
            .should().dependOnClassesThat().areAnnotatedWith(Entity.class);

    @ArchTest
    static final ArchRule noTransactionsInControllers = noClasses()
            .that().resideInAPackage("..api..")
            .should().beAnnotatedWith(Transactional.class)
            .orShould().dependOnClassesThat().areAssignableTo(Transactional.class);

    @ArchTest
    static final ArchRule noFieldInjection = noFields().should().beAnnotatedWith(Autowired.class);

    @ArchTest
    static final ArchRule ticketHasNoSetters = noMethods()
            .that().areDeclaredIn(Ticket.class).and().arePublic()
            .should().haveNameStartingWith("set");

    @ArchTest
    static final ArchRule onlyTheDomainRejectsTransitions = noClasses()
            .that().resideOutsideOfPackage("..ticket.domain..")
            .should().callConstructor(InvalidStatusTransitionException.class, Long.class, TicketStatus.class,
                    TicketStatus.class);
}
