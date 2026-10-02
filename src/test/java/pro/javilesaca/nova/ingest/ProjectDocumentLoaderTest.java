package pro.javilesaca.nova.ingest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pro.javilesaca.nova.config.NovaProperties;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La ingesta se prueba como Java normal: ficheros .mdx de mentira en un
 * directorio temporal, sin modelo ni vectores (el split no necesita embeddings).
 */
class ProjectDocumentLoaderTest {

    @TempDir
    Path tempDir;

    private ProjectDocumentLoader loader() {
        return new ProjectDocumentLoader(
                new NovaProperties(tempDir.toString(), tempDir.resolve("v.json").toString(), 5, 0.0));
    }

    @Test
    void loadsMdxWithProjectMetadataAndSkipsFrontmatter() throws Exception {
        Files.writeString(tempDir.resolve("event-dashboard.mdx"), """
                ---
                title: "Panel de Eventos (EventDashboard)"
                techStack: ["Java"]
                ---
                Microservicio Spring Boot para capturar eventos en tiempo real.
                Con validación, paginación y SSE.
                """);

        var docs = loader().load();

        assertThat(docs).isNotEmpty();
        // El frontmatter no se indexa: es ruido para el modelo.
        assertThat(docs).allSatisfy(d -> assertThat(d.getText()).doesNotContain("techStack"));
        assertThat(docs.get(0).getMetadata())
                .containsEntry("project", "event-dashboard")
                .containsEntry("title", "Panel de Eventos (EventDashboard)");
    }

    @Test
    void unreadableFileDoesNotBreakIngestion() throws Exception {
        // Directorio vacío: cero documentos, cero excepciones.
        assertThat(loader().load()).isEmpty();
    }

    @Test
    void ignoresNonMdxFiles() throws Exception {
        Files.writeString(tempDir.resolve("README.md"), "hola");

        assertThat(loader().load()).isEmpty();
    }

    @Test
    void frontmatterOnlyFileIsSynthesizedIntoIndexableText() throws Exception {
        // Las fichas del portfolio son SOLO frontmatter: deben indexarse igual.
        Files.writeString(tempDir.resolve("event-dashboard.mdx"), """
                ---
                title: "Panel de Eventos"
                description: "API de eventos en tiempo real."
                techStack: ["Java", "Spring Boot"]
                challenges:
                  - "Emitir eventos en vivo por SSE"
                ---
                """);

        var docs = loader().load();

        assertThat(docs).isNotEmpty();
        assertThat(docs.get(0).getText())
                .contains("Panel de Eventos")
                .contains("tiempo real")
                .contains("SSE");
    }
}
