package pro.javilesaca.nova.ingest;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;
import pro.javilesaca.nova.config.NovaProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Convierte los .mdx del portfolio en {@link Document}s listos para indexar.
 *
 * <p>¿Por qué trocear? Los embeddings tienen límite de tamaño y la recuperación
 * funciona mejor con trozos enfocados: un trozo = una idea = una cita precisa.
 * {@link TokenTextSplitter} corta por tokens (lo que entiende el modelo), no
 * por caracteres a lo bruto.
 *
 * <p>Cada documento lleva metadatos (proyecto, idioma, título): son lo que
 * luego mostramos como CITAS. Un RAG sin citas es un loro; con citas, evidencia.
 */
@Component
public class ProjectDocumentLoader {

    private final NovaProperties properties;
    private final TokenTextSplitter splitter = new TokenTextSplitter();

    public ProjectDocumentLoader(NovaProperties properties) {
        this.properties = properties;
    }

    /** Lee todos los .mdx de contentDir y devuelve los trozos listos. */
    public List<Document> load() throws IOException {
        Path dir = Path.of(properties.contentDir());
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(p -> p.toString().endsWith(".mdx"))
                    .flatMap(p -> splitFile(p).stream())
                    .toList();
        }
    }

    /** Un fichero → varios Document con metadatos del proyecto. */
    List<Document> splitFile(Path file) {
        String raw;
        try {
            raw = Files.readString(file);
        } catch (IOException e) {
            // Un fichero ilegible no tumba la ingesta: se registra y se sigue.
            System.err.println("[NOVA] No se pudo leer " + file + ": " + e.getMessage());
            return List.of();
        }
        String project = file.getFileName().toString().replace(".mdx", "");
        String title = frontmatter(raw, "title");
        Map<String, Object> metadata = Map.of(
                "project", project,
                "title", title.isBlank() ? project : title,
                // El idioma se deduce de la carpeta (../projects/es/...). Así la
                // cita puede enlazar a la ficha correcta del portfolio.
                "lang", file.toAbsolutePath().getParent().getFileName().toString());
        // El frontmatter (---...---) es ruido para el modelo: solo indexamos el cuerpo.
        String body = raw.replaceFirst("(?s)^---.*?---\\s*", "");
        Document doc = new Document(body, metadata);
        return splitter.split(List.of(doc));
    }

    /** Extrae un campo simple del frontmatter YAML (title: "..."). */
    private String frontmatter(String raw, String key) {
        return raw.lines()
                .filter(l -> l.startsWith(key + ":"))
                .map(l -> l.substring(key.length() + 1).trim().replaceAll("^\"|\"$", ""))
                .findFirst()
                .orElse("");
    }
}
