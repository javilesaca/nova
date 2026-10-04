package pro.javilesaca.nova.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Valida el esquema del eval dorado sin red ni clave de embeddings.
 *
 * <p>Cada caso necesita pregunta no vacía y lista de proyectos esperados
 * (vacía solo si admite "no lo sé"). La comprobación contra el índice real
 * (recuperar los proyectos esperados) es el siguiente paso y necesita clave.
 */
class GoldenSetTest {

    /** Slugs del corpus español (ver portfolio-astro/src/content/projects/es). */
    private static final List<String> SLUGS = List.of(
            "agente-gamer", "event-dashboard", "hockey-pong", "memory-cards", "ranking-videojuegos");

    @Test
    void goldenTieneEsquemaValido() throws Exception {
        List<Map<String, Object>> casos;
        // Fuente única: evals/ de la raíz (surefire corre con basedir = módulo).
        Path golden = Paths.get("evals/golden.json");
        assertTrue(Files.isRegularFile(golden), "evals/golden.json debe existir en la raíz del repo");
        try (var in = Files.newInputStream(golden)) {
            casos = new ObjectMapper().readValue(in, new TypeReference<>() {});
        }
        assertFalse(casos.isEmpty(), "el golden set no puede estar vacío");
        for (Map<String, Object> caso : casos) {
            String id = (String) caso.get("id");
            assertNotNull(id, "cada caso necesita id");
            String pregunta = (String) caso.get("question");
            assertNotNull(pregunta, id + ": necesita question");
            assertFalse(pregunta.isBlank(), id + ": question vacía");
            @SuppressWarnings("unchecked")
            List<String> esperados = (List<String>) caso.get("expectedProjects");
            assertNotNull(esperados, id + ": necesita expectedProjects");
            for (String slug : esperados) {
                assertTrue(SLUGS.contains(slug), id + ": slug desconocido " + slug);
            }
            if (esperados.isEmpty()) {
                assertEquals(Boolean.TRUE, caso.get("expectAdmission"),
                        id + ": sin proyectos esperados debe admitir 'no lo sé'");
            }
        }
    }
}
