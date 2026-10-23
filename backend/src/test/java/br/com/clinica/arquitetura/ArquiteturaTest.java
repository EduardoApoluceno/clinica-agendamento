package br.com.clinica.arquitetura;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Regras da Clean Architecture por módulo (ADR-0003). */
@AnalyzeClasses(packages = "br.com.clinica", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquiteturaTest {

    @ArchTest
    static final ArchRule dominioSemFrameworks = noClasses()
            .that().resideInAPackage("br.com.clinica..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "org.hibernate..",
                    "br.com.clinica..application..", "br.com.clinica..adapters..",
                    "br.com.clinica..infrastructure..")
            .allowEmptyShould(true)
            .because("o domínio deve ser puro e testável sem Spring");

    @ArchTest
    static final ArchRule aplicacaoNaoConheceAdaptadores = noClasses()
            .that().resideInAPackage("br.com.clinica..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "br.com.clinica..adapters..", "br.com.clinica..infrastructure..",
                    "jakarta.persistence..", "org.springframework.web..")
            .allowEmptyShould(true)
            .because("casos de uso dependem apenas de portas");

    @ArchTest
    static final ArchRule controllersNaoAcessamPersistencia = noClasses()
            .that().resideInAPackage("br.com.clinica..adapters.in..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "br.com.clinica..adapters.out..", "org.springframework.data..")
            .allowEmptyShould(true)
            .because("controllers chamam casos de uso, nunca repositórios");

    @ArchTest
    static final ArchRule semAcessoDiretoAoJdbcForaDosAdaptadores = noClasses()
            .that().resideOutsideOfPackages("br.com.clinica..adapters.out..", "br.com.clinica..infrastructure..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc..", "java.sql..")
            .allowEmptyShould(true);
}
