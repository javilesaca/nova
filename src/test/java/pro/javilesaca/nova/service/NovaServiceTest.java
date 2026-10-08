package pro.javilesaca.nova.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import pro.javilesaca.nova.config.NovaProperties;
import pro.javilesaca.nova.config.NovaProperties.Owner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import org.mockito.quality.Strictness;

/**
 * Fast-path fuera de ámbito: canned sin tocar el modelo ni el vector store.
 */
class NovaServiceTest {

    private ChatClient chatClient;
    private SimpleVectorStore vectorStore;
    private NovaService service;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class,
                withSettings().strictness(Strictness.LENIENT).defaultAnswer(org.mockito.Answers.RETURNS_DEEP_STUBS));
        when(chatClient.prompt().user(anyString()).call().content()).thenReturn("respuesta portfolio");
        vectorStore = mock(SimpleVectorStore.class,
                withSettings().strictness(Strictness.LENIENT));
        when(vectorStore.similaritySearch(org.mockito.ArgumentMatchers.any(org.springframework.ai.vectorstore.SearchRequest.class))).thenReturn(List.of());
        ChatClient.Builder builder = mock(ChatClient.Builder.class,
                withSettings().strictness(Strictness.LENIENT)
                        .defaultAnswer(org.mockito.Answers.RETURNS_SELF));
        when(builder.build()).thenReturn(chatClient);
        service = new NovaService(builder, vectorStore,
                new NovaProperties("src/test/resources/content", "target/test-store.json", 5, 0.5));
        // El stubbing de arriba registra invocaciones en los mocks: se limpian
        // para que los verify(never()) midan solo lo que hace el fast-path.
        clearInvocations(chatClient, vectorStore);
    }

    @Test
    void tiempoRespondeCannedSinLlamarAlModelo() {
        NovaAnswer answer = service.ask("¿Qué tiempo hace hoy en Madrid?");

        assertThat(answer.answer())
                .isEqualTo("No es mi función: solo respondo sobre el portfolio de Javier y sus proyectos.");
        assertThat(answer.citations()).isEmpty();
        verify(chatClient, never()).prompt();
        verify(vectorStore, never()).similaritySearch(org.mockito.ArgumentMatchers.any(org.springframework.ai.vectorstore.SearchRequest.class));
    }

    @Test
    void horaYFechaRespondenCannedDeTiempo() {
        assertThat(service.ask("¿Qué hora es?").answer()).contains("No es mi función");
        assertThat(service.ask("¿A qué fecha estamos?").answer()).contains("No es mi función");
        verify(chatClient, never()).prompt();
    }

    @Test
    void chisteYayudaRespondenCannedGeneral() {
        NovaAnswer chiste = service.ask("Cuéntame un chiste");
        assertThat(chiste.answer()).contains("No estoy entrenado para esa tarea");
        assertThat(chiste.answer()).contains("Ranking de Videojuegos");
        assertThat(chiste.citations()).isEmpty();

        NovaAnswer ayuda = service.ask("¿Puedes darme ayuda general?");
        assertThat(ayuda.answer()).contains("No estoy entrenado para esa tarea");
        assertThat(ayuda.citations()).isEmpty();

        verify(chatClient, never()).prompt();
    }

    @Test
    void preguntaPortfolioSigueYendoAlModelo() {
        NovaAnswer answer = service.ask("¿Qué proyecto usa eventos en tiempo real?");

        assertThat(answer.answer()).isEqualTo("respuesta portfolio");
    }

    @Test
    void tiempoRealDelPortfolioNoEsElClima() {
        NovaAnswer answer = service.ask("¿Qué proyecto usa eventos en tiempo real?");

        assertThat(answer.answer()).isEqualTo("respuesta portfolio");
    }

    private NovaService serviceWithOwner() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class,
                withSettings().strictness(Strictness.LENIENT)
                        .defaultAnswer(org.mockito.Answers.RETURNS_SELF));
        when(builder.build()).thenReturn(chatClient);
        return new NovaService(builder, vectorStore,
                new NovaProperties("src/test/resources/content", "target/test-store.json", 5, 0.5,
                        new Owner("Javier Lesaca Medina", "IT Infrastructure & Software Developer",
                                "Trayectoria de infraestructura a software e IA",
                                List.of("Infra", "Software"),
                                "Une infra y operaciones con software+IA",
                                List.of("¿Qué experiencia tiene?"))));
    }

    @Test
    void identidadQuienEresRespondeSinLlamarAlModelo() {
        NovaService owned = serviceWithOwner();
        clearInvocations(chatClient, vectorStore);

        NovaAnswer answer = owned.ask("¿Quién eres?");

        assertThat(answer.answer()).contains("Javier Lesaca Medina");
        assertThat(answer.citations()).isEmpty();
        verify(chatClient, never()).prompt();
        verify(vectorStore, never()).similaritySearch(org.mockito.ArgumentMatchers.any(org.springframework.ai.vectorstore.SearchRequest.class));
    }

    @Test
    void identidadExperienciaYContratarRespondenSinLlamarAlModelo() {
        NovaService owned = serviceWithOwner();
        clearInvocations(chatClient, vectorStore);

        assertThat(owned.ask("¿Qué experiencia tiene?").answer()).contains("Javier Lesaca Medina");
        assertThat(owned.ask("¿Por qué contratarle?").answer()).contains("Une infra");
        verify(chatClient, never()).prompt();
    }

    @Test
    void sinOwnerNoInterceptaIdentidad() {
        NovaAnswer answer = service.ask("¿Quién eres?");

        assertThat(answer.answer()).isEqualTo("respuesta portfolio");
    }

    @Test
    void llmPathHaceUnaSolaRecuperacion() {
        NovaAnswer answer = service.ask("¿Qué proyecto Acme usa eventos en tiempo real?");

        assertThat(answer.answer()).isEqualTo("respuesta portfolio");
        verify(vectorStore, times(1)).similaritySearch(
                org.mockito.ArgumentMatchers.any(org.springframework.ai.vectorstore.SearchRequest.class));
        verify(chatClient, times(1)).prompt();
    }

    @Test
    void falloDelModeloRespondeCannedAmable() {
        when(chatClient.prompt().user(anyString()).call().content())
                .thenThrow(new RuntimeException("429 cuota excedida"));

        NovaAnswer answer = service.ask("¿Qué proyecto Acme usa cola de mensajes?");

        assertThat(answer.answer()).contains("saturado o sin cuota");
        assertThat(answer.citations()).isEmpty();
    }

    @Test
    void falloDelEmbeddingRespondeCannedAmable() {
        when(vectorStore.similaritySearch(
                org.mockito.ArgumentMatchers.any(org.springframework.ai.vectorstore.SearchRequest.class)))
                .thenThrow(new RuntimeException("red caida"));

        NovaAnswer answer = service.ask("¿Qué proyecto Acme usa cache distribuida?");

        assertThat(answer.answer()).contains("saturado o sin cuota");
        assertThat(answer.citations()).isEmpty();
    }

    @Test
    void preguntaRepetidaSaleDeCache() {
        String q = "¿Qué proyecto Acme usa eventos en tiempo real?";
        service.ask(q);
        service.ask(q);

        verify(vectorStore, times(1)).similaritySearch(
                org.mockito.ArgumentMatchers.any(org.springframework.ai.vectorstore.SearchRequest.class));
        verify(chatClient, times(1)).prompt();
    }
}
