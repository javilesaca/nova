package pro.javilesaca.nova.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pro.javilesaca.nova.service.NovaAnswer;
import pro.javilesaca.nova.service.NovaService;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
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
        when(nova.ask(anyString())).thenReturn(new NovaAnswer(
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
    void askRejectsBlankQuestion() throws Exception {
        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":""}"""))
                .andExpect(status().isBadRequest());
    }
}
