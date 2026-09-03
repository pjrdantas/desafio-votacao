package br.com.sicredi.desafiovotacao.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "br.com.sicredi.desafiovotacao", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquiteturaTest {
    @ArchTest
    static final ArchRule dominioIndependente = noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta..", "java.sql..", "io.micrometer..", "io.opentelemetry..", "com.dynatrace..", "..adapter..", "..application..", "..config..");

    @ArchTest
    static final ArchRule aplicacaoIndependente = noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta..", "java.sql..", "io.micrometer..", "io.opentelemetry..", "com.dynatrace..", "..adapter..", "..config..");

    @ArchTest
    static final ArchRule entradaNaoAcessaPersistencia = noClasses().that().resideInAPackage("..adapter.in..")
            .should().dependOnClassesThat().resideInAnyPackage("..adapter.out..", "..application.port.out..");
}
