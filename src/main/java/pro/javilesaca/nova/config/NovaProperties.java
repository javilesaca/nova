package pro.javilesaca.nova.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuración tipada de NOVA.
 *
 * @param contentDir       carpeta con los .mdx del portfolio a indexar
 *                         (ej. portfolio-astro/src/content/projects/es).
 * @param storeFile        fichero JSON donde persiste el vector store. Indexar
 *                         cuesta embeddings (= llamadas al modelo), así que el
 *                         índice se guarda y se reutiliza entre arranques.
 * @param topK             cuántos trozos recupera cada pregunta. 5 es el
 *                         equilibrio: suficiente contexto, poco ruido.
 * @param similarityThreshold nota mínima (0-1) para aceptar un trozo. Frena
 *                         que el modelo "rellene" con contenido tangencial.
 */
@ConfigurationProperties(prefix = "nova")
public record NovaProperties(
        String contentDir,
        String storeFile,
        int topK,
        double similarityThreshold,
        Owner owner
) {
    /** Identidad del dueño del portfolio: todo opcional, vacío = sin identidad. */
    public record Owner(
            String name,
            String role,
            String bio,
            List<String> areas,
            String hire,
            List<String> chips
    ) {
        public Owner {
            if (name == null) name = "";
            if (role == null) role = "";
            if (bio == null) bio = "";
            if (areas == null) areas = List.of();
            if (hire == null) hire = "";
            if (chips == null) chips = List.of();
        }
    }

    public NovaProperties {
        if (contentDir == null || contentDir.isBlank()) {
            throw new IllegalArgumentException("nova.content-dir es obligatorio");
        }
        if (topK < 1) {
            throw new IllegalArgumentException("nova.top-k debe ser >= 1");
        }
        if (owner == null) {
            owner = new Owner("", "", "", List.of(), "", List.of());
        }
    }

}
