package pro.javilesaca.nova.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.stereotype.Service;
import pro.javilesaca.nova.config.NovaProperties;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    /** Pregunta libre → respuesta + citas. */
    public NovaAnswer ask(String question) {
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
