package pro.javilesaca.nova.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.stereotype.Service;
import pro.javilesaca.nova.config.NovaProperties;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * El cerebro de NOVA: pregunta → recupera contexto → responde con citas.
 *
 * <p>RAG manual con UNA sola recuperación: un {@code similaritySearch} por
 * pregunta; los mismos documentos alimentan el contexto del prompt y las
 * citas. Sin advisor de retrieval: el contexto se inyecta a mano en el
 * mensaje de usuario.
 *
 * <p>Las citas salen de los metadatos de los documentos recuperados (ver
 * ProjectDocumentLoader): el modelo genera el texto, Java construye la evidencia.
 * Nunca dejamos que el modelo "invente" las fuentes.
 */
@Service
public class NovaService {

    private static final Logger log = LoggerFactory.getLogger(NovaService.class);

    /** Quién es NOVA y sus límites. El tono lo pondrá la cara (hito 2). */
    private static final String SYSTEM_PROMPT_BASE = """
            Eres NOVA, la asistente del portfolio.
            Respondes preguntas sobre el perfil y los proyectos usando SOLO el
            contexto proporcionado. Reglas:
            1. Si el contexto no contiene la respuesta, dilo claramente y sugiere
               sobre qué SÍ puedes responder. Nunca inventes datos.
            2. Escribe siempre en español, tono cercano y conciso.
            3. Cuando afirmes algo sobre un proyecto, menciónalo por su título.
            """;

    /** Degradación amable ante 429/5xx/red: HTTP 200 con este texto y sin citas. */
    static final String OVERLOADED_CANNED =
            "El servicio de IA está saturado o sin cuota. Prueba de nuevo más tarde.";

    static final long CACHE_TTL_MS = 10 * 60 * 1000L;
    static final int CACHE_MAX = 100;

    private record CachedAnswer(NovaAnswer answer, long atMs) {}

    /** LRU ~100 con TTL 10 min; acceso bajo synchronized(cache). */
    private final Map<String, CachedAnswer> cache = new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CachedAnswer> eldest) {
            return size() > CACHE_MAX;
        }
    };

    private final ChatClient chatClient;
    private final SimpleVectorStore vectorStore;
    private final NovaProperties properties;

    public NovaService(ChatClient.Builder builder, SimpleVectorStore vectorStore, NovaProperties properties) {
        this.properties = properties;
        this.vectorStore = vectorStore;
        this.chatClient = builder
                .defaultSystem(buildSystemPrompt(properties))
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
     * normal cuesta 1 embedding + 1 chat.
     */
    private static final Set<String> WEATHER_TOKENS = Set.of(
            "tiempo", "clima", "llueve", "temperatura", "hora", "fecha", "hoy");
    private static final Set<String> GENERAL_TOKENS = Set.of(
            "chiste", "ayuda", "help");
    /** Pedidos de chiste/ayuda son cortos; las preguntas de proyecto son largas. */
    private static final int GENERAL_MAX_TOKENS = 4;
    /** Saludos: solo preguntas cortas; con más tokens va al RAG. Mismo umbral que GENERAL. */
    private static final int SALUDO_MAX_TOKENS = GENERAL_MAX_TOKENS;
    private static final Set<String> SALUDO_TOKENS = Set.of(
            "hola", "buenas", "buenos", "dias", "tardes", "noches",
            "hey", "hello", "gracias", "adios", "chau");
    private static final String WEATHER_CANNED =
            "No es mi función: solo respondo sobre el portfolio de Javier y sus proyectos.";
    private static final String GENERAL_CANNED =
            "No estoy entrenado para esa tarea: respondo solo sobre el perfil y los proyectos de Javier. "
                    + "Puedo contarte sobre Ranking de Videojuegos, Hockey Pong o Memory Card.";

    /**
     * IDENTIDAD genérica: responde con el {@code owner} configurado, sin LLM.
     * Si {@code owner.name} está vacío, no intercepta (pasa al flujo normal).
     */
    java.util.Optional<NovaAnswer> identityAnswer(String normalized) {
        NovaProperties.Owner owner = properties.owner();
        if (owner == null || owner.name() == null || owner.name().isBlank()) {
            return java.util.Optional.empty();
        }
        String ownerNorm = normalize(owner.name());
        String firstName = ownerNorm.split("\\s+")[0];
        boolean who = normalized.contains("quien eres")
                || normalized.contains("quien es " + ownerNorm)
                || (normalized.contains("quien es") && normalized.contains(firstName))
                || normalized.contains("a que te dedicas")
                || normalized.contains("sobre ti");
        boolean experience = normalized.contains("experiencia");
        boolean hire = normalized.contains("por que contratar") || normalized.contains("contratar");
        if (!who && !experience && !hire) {
            return java.util.Optional.empty();
        }
        String areas = owner.areas() == null || owner.areas().isEmpty()
                ? ""
                : " Áreas: " + String.join(", ", owner.areas()) + ".";
        String answer;
        if (hire) {
            answer = "¿Por qué contratar a " + owner.name() + "? " + owner.hire();
        } else if (experience) {
            answer = "Experiencia de " + owner.name() + " (" + owner.role() + "): "
                    + owner.bio() + "." + areas;
        } else {
            answer = owner.name() + " es " + owner.role() + ". " + owner.bio() + "." + areas;
        }
        return java.util.Optional.of(new NovaAnswer(answer.trim(), List.of()));
    }

    /** SYSTEM_PROMPT compuesto con owner cuando existe (una línea rol+áreas). */
    static String buildSystemPrompt(NovaProperties properties) {
        NovaProperties.Owner owner = properties == null ? null : properties.owner();
        if (owner == null || owner.name() == null || owner.name().isBlank()) {
            return SYSTEM_PROMPT_BASE;
        }
        String areas = owner.areas() == null || owner.areas().isEmpty()
                ? "" : " Áreas: " + String.join(", ", owner.areas()) + ".";
        return SYSTEM_PROMPT_BASE + "\nHablas de " + owner.name() + " (" + owner.role() + ")." + areas;
    }

    /** Minúsculas + sin tildes para comparar tokens ("¿Qué tiempo hace?" → "que tiempo hace"). */
    static String normalize(String question) {
        String lower = question.toLowerCase(java.util.Locale.ROOT);
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "");
    }

    /** Clave de caché: misma normalización del fast-path + contexto. */
    static String cacheKey(String normalized, String context) {
        return normalized + "\u0000" + (context == null ? "" : context);
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
     * <p>Una sola recuperación: el {@code similaritySearch} global alimenta a la
     * vez el contexto del prompt y las citas. Si {@code context} está en la
     * whitelist, ambos se filtran en memoria por metadato {@code project};
     * en otro caso se mantiene la búsqueda global.
     */
    public NovaAnswer ask(String question, String context) {
        // 0a. IDENTIDAD genérica: canned con owner, sin modelo ni vector store.
        String normalized = normalize(question);
        java.util.Optional<NovaAnswer> identity = identityAnswer(normalized);
        if (identity.isPresent()) {
            return identity.get();
        }
        // 0b. Fast-path fuera de ámbito: canned + citas vacías, sin modelo ni vector store.
        java.util.List<String> words = java.util.Arrays.stream(normalized.split("[^a-z]+"))
                .filter(s -> !s.isEmpty())
                .toList();
        Set<String> tokens = Set.copyOf(words);
        // "tiempo real" (eventos/SSE del portfolio) no es el clima: se excluye del token "tiempo".
        boolean weather = tokens.stream()
                .anyMatch(t -> WEATHER_TOKENS.contains(t)
                        && !(t.equals("tiempo") && normalized.contains("tiempo real")));
        if (weather) {
            return new NovaAnswer(WEATHER_CANNED, List.of());
        }
        if (words.size() <= GENERAL_MAX_TOKENS && tokens.stream().anyMatch(GENERAL_TOKENS::contains)) {
            return new NovaAnswer(GENERAL_CANNED, List.of());
        }
        if (words.size() <= SALUDO_MAX_TOKENS && tokens.stream().anyMatch(SALUDO_TOKENS::contains)) {
            return new NovaAnswer(GENERAL_CANNED, List.of());
        }
        // 0c. Caché de respuestas LLM: pregunta normalizada + contexto.
        String key = cacheKey(normalized, context);
        synchronized (cache) {
            CachedAnswer cached = cache.get(key);
            if (cached != null) {
                if (System.currentTimeMillis() - cached.atMs() < CACHE_TTL_MS) {
                    return cached.answer();
                }
                cache.remove(key);
            }
        }
        try {
            // 1. ÚNICA recuperación: mismos docs para el prompt y las citas.
            List<Document> retrieved = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(question)
                            .topK(properties.topK())
                            .similarityThreshold(properties.similarityThreshold())
                            .build());
            List<Document> sources = retrieved == null ? List.of() : retrieved;
            if (context != null && CONTEXT_WHITELIST.contains(context)) {
                sources = sources.stream()
                        .filter(d -> context.equals(String.valueOf(d.getMetadata().getOrDefault("project", ""))))
                        .toList();
            }
            // 2. Contexto inyectado a mano en el prompt.
            String extracts = sources.stream()
                    .map(d -> d.getText() == null ? "" : d.getText())
                    .collect(Collectors.joining("\n---\n"));
            String answer = chatClient.prompt().user("Contexto:\n" + extracts + "\n\n" + question).call().content();
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
            NovaAnswer result = new NovaAnswer(answer == null ? "" : answer, citations);
            synchronized (cache) {
                cache.put(key, new CachedAnswer(result, System.currentTimeMillis()));
            }
            return result;
        } catch (Exception e) {
            log.warn("Nova LLM no disponible ({}): {}", e.getClass().getSimpleName(), e.getMessage());
            return new NovaAnswer(OVERLOADED_CANNED, List.of());
        }
    }
}
