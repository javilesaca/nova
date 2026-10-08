package pro.javilesaca.nova.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.stereotype.Service;
import pro.javilesaca.nova.config.NovaProperties;

import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * El cerebro de NOVA: pregunta → recupera contexto → responde con citas.
 *
 * <p>El {@link RetrievalAugmentationAdvisor} hace el RAG por nosotros: busca los top-K
 * trozos en el vector store, los inyecta como contexto y ordena al modelo
 * responder SOLO con ellos. Es el antídoto contra las alucinaciones: si la
 * respuesta no está en tus proyectos, el modelo debe decir que no lo sabe.
 *
 * <p>Ojo a la versión: en Spring AI 1.0.x el RAG es modular (RetrievalAugmentationAdvisor
 * + retrievers). El antiguo QuestionAnswerAdvisor de tutoriales viejos ya no existe.
 *
 * <p>Las citas salen de los metadatos de los documentos recuperados (ver
 * ProjectDocumentLoader): el modelo genera el texto, Java construye la evidencia.
 * Nunca dejamos que el modelo "invente" las fuentes.
 */
@Service
public class NovaService {

    /** Quién es NOVA y sus límites. El tono lo pondrá la cara (hito 2). */
    private static final String SYSTEM_PROMPT = """
            Eres NOVA, la asistente del portfolio de Javier Lesaca Medina.
            Respondes preguntas sobre su perfil y sus proyectos usando SOLO el
            contexto proporcionado. Reglas:
            1. Si el contexto no contiene la respuesta, dilo claramente y sugiere
               sobre qué SÍ puedes responder. Nunca inventes datos.
            2. Escribe siempre en español, tono cercano y conciso.
            3. Cuando afirmes algo sobre un proyecto, menciónalo por su título.
            """;

    private final ChatClient chatClient;
    private final SimpleVectorStore vectorStore;
    private final NovaProperties properties;

    public NovaService(ChatClient.Builder builder, SimpleVectorStore vectorStore, NovaProperties properties) {
        this.properties = properties;
        this.vectorStore = vectorStore;
        this.chatClient = builder
                .defaultSystem(SYSTEM_PROMPT)
                // Piezas del RAG, cada una con una sola responsabilidad:
                // retriever = CÓMO buscar (top-K sobre nuestro índice),
                // advisor = CUÁNDO y CÓMO inyectar lo recuperado al modelo.
                .defaultAdvisors(RetrievalAugmentationAdvisor.builder()
                        .documentRetriever(VectorStoreDocumentRetriever.builder()
                                .vectorStore(vectorStore)
                                .topK(properties.topK())
                                .similarityThreshold(properties.similarityThreshold())
                                .build())
                        .build())
                .build();
    }

    /**
     * Proyectos filtrables vía {@code context} (paridad con {@code nova-stellar/api/ask.php}).
     * Solo un valor de esta lista activa el filtrado por metadato {@code project};
     * cualquier otro valor (null, vacío o no-whitelist) mantiene la búsqueda global.
     */
    private static final Set<String> CONTEXT_WHITELIST = Set.of(
            "agente-gamer", "event-dashboard", "ranking-videojuegos", "hockey-pong", "memory-cards");

    /**
     * Fast-path fuera de ámbito: responde sin tocar el vector store ni el modelo.
     * Va ANTES de cualquier llamada a embedding/chat porque cada {@code ask}
     * normal cuesta 2 embeddings + 1 chat (≈40-60 s con Gemini en local).
     */
    private static final Set<String> WEATHER_TOKENS = Set.of(
            "tiempo", "clima", "llueve", "temperatura", "hora", "fecha", "hoy");
    private static final Set<String> GENERAL_TOKENS = Set.of(
            "chiste", "ayuda", "help");
    private static final String WEATHER_CANNED =
            "No es mi función: solo respondo sobre el portfolio de Javier y sus proyectos.";
    private static final String GENERAL_CANNED =
            "No estoy entrenado para esa tarea: respondo solo sobre el perfil y los proyectos de Javier. "
                    + "Puedo contarte sobre Ranking de Videojuegos, Hockey Pong o Memory Card.";

    /** Minúsculas + sin tildes para comparar tokens ("¿Qué tiempo hace?" → "que tiempo hace"). */
    static String normalize(String question) {
        String lower = question.toLowerCase(java.util.Locale.ROOT);
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "");
    }

    /**
     * Compat: pregunta global sin filtro de proyecto. Delega a {@link #ask(String, String)} con contexto nulo.
     */
    public NovaAnswer ask(String question) {
        return ask(question, null);
    }

    /**
     * Pregunta con filtro opcional de proyecto.
     *
     * <p>Si {@code context} coincide exactamente con la whitelist, el retrieval y las citas
     * se filtran en memoria por metadato {@code project == context} tras el
     * {@code similaritySearch} global; si el filtrado deja vacío, las citas quedan vacías
     * sin fallback global. En cualquier otro caso (null, vacío o no-whitelist) se mantiene
     * el comportamiento global actual. La respuesta del modelo sigue generándose con el
     * advisor global; el filtrado garantiza la paridad de evidencias con el lab PHP.
     */
    public NovaAnswer ask(String question, String context) {
        // 0. Fast-path fuera de ámbito: canned + citas vacías, sin modelo ni vector store.
        String normalized = normalize(question);
        Set<String> tokens = Set.of(normalized.split("[^a-z]+"));
        // "tiempo real" (eventos/SSE del portfolio) no es el clima: se excluye del token "tiempo".
        boolean weather = tokens.stream()
                .anyMatch(t -> WEATHER_TOKENS.contains(t)
                        && !(t.equals("tiempo") && normalized.contains("tiempo real")));
        if (weather) {
            return new NovaAnswer(WEATHER_CANNED, List.of());
        }
        if (tokens.stream().anyMatch(GENERAL_TOKENS::contains)) {
            return new NovaAnswer(GENERAL_CANNED, List.of());
        }
        // 1. El modelo responde con el contexto ya inyectado por el advisor.
        String answer = chatClient.prompt().user(question).call().content();
        // 2. Recuperamos LOS MISMOS trozos para construir las citas en Java.
        //    (El advisor los usa internamente; aquí los pedimos para evidencia.)
        List<Document> sources = Optional.ofNullable(vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(question)
                                .topK(properties.topK())
                                .similarityThreshold(properties.similarityThreshold())
                                .build()))
                .orElse(List.of());
        if (context != null && CONTEXT_WHITELIST.contains(context)) {
            sources = sources.stream()
                    .filter(d -> context.equals(String.valueOf(d.getMetadata().getOrDefault("project", ""))))
                    .toList();
        }
        List<NovaAnswer.Citation> citations = sources.stream()
                .collect(Collectors.toMap(
                        d -> d.getId(), d -> d,
                        (a, b) -> a)) // por si el store devuelve duplicados
                .values().stream()
                .map(d -> {
                    Map<String, Object> meta = d.getMetadata();
                    String text = d.getText() == null ? "" : d.getText();
                    return new NovaAnswer.Citation(
                            String.valueOf(meta.getOrDefault("project", "?")),
                            String.valueOf(meta.getOrDefault("title", "?")),
                            text.length() > 240 ? text.substring(0, 240) + "…" : text);
                })
                .toList();
        return new NovaAnswer(answer == null ? "" : answer, citations);
    }
}
