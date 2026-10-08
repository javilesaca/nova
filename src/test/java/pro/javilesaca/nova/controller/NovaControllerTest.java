package pro.javilesaca.nova.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pro.javilesaca.nova.service.NovaAnswer;
import pro.javilesaca.nova.service.NovaService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP de NOVA sin levantar Spring ni llamar a ningún LLM.
 *
 * <p>MockMvc "standalone": instanciamos el controlador a mano con un servicio
 * mockeado. Rápido y sin contexto (el cableado real se verifica al arrancar
 * con una key de verdad).
 */
class NovaControllerTest {

    private final NovaService nova = mock(NovaService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new NovaController(nova)).build();

    @Test
    void askReturnsAnswerWithCitations() throws Exception {
        when(nova.ask(anyString(), any())).thenReturn(new NovaAnswer(
                "Javier usa Spring Boot",
                List.of(new NovaAnswer.Citation("event-dashboard", "Panel de Eventos", "Microservicio Spring Boot…"))));

        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"¿Qué stack usa Javier?"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Javier usa Spring Boot"))
                .andExpect(jsonPath("$.citations[0].project").value("event-dashboard"));
    }

    @Test
    void askWithValidContextForwardsItToService() throws Exception {
        when(nova.ask(eq("¿Qué hace hockey-pong?"), eq("hockey-pong"))).thenReturn(new NovaAnswer(
                "Un Pong de hockey",
                List.of(new NovaAnswer.Citation("hockey-pong", "Hockey Pong", "Juego…"))));

        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"¿Qué hace hockey-pong?","context":"hockey-pong"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citations[0].project").value("hockey-pong"));

        verify(nova).ask("¿Qué hace hockey-pong?", "hockey-pong");
    }

    @Test
    void askWithInvalidContextFallsBackToGlobal() throws Exception {
        when(nova.ask(eq("¿Qué stack usa Javier?"), eq("no-existe"))).thenReturn(new NovaAnswer(
                "Javier usa Spring Boot",
                List.of(new NovaAnswer.Citation("event-dashboard", "Panel de Eventos", "Microservicio Spring Boot…"))));

        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"¿Qué stack usa Javier?","context":"no-existe"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citations[0].project").value("event-dashboard"));

        verify(nova).ask("¿Qué stack usa Javier?", "no-existe");
    }

    @Test
    void askToleratesUnknownFields() throws Exception {
        when(nova.ask(anyString(), any())).thenReturn(new NovaAnswer("ok", List.of()));

        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"hola","context":null,"extra":"ignorado"}"""))
                .andExpect(status().isOk());
    }

    @Test
    void askRejectsBlankQuestion() throws Exception {
        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":""}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void saturadoSigueSiendo200() throws Exception {
        when(nova.ask(anyString(), any())).thenReturn(new NovaAnswer(
                "El servicio de IA está saturado o sin cuota. Prueba de nuevo más tarde.", List.of()));

        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"¿Qué proyecto Acme hay?"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer")
                        .value("El servicio de IA está saturado o sin cuota. Prueba de nuevo más tarde."));
    }
}
