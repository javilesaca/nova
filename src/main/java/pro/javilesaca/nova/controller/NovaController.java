package pro.javilesaca.nova.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pro.javilesaca.nova.service.NovaAnswer;
import pro.javilesaca.nova.service.NovaService;

/**
 * Puerta HTTP de NOVA (hito 1: solo API, la cara llega en el hito 2).
 *
 * <p>{@code POST /api/ask {"question": "..."}} → {@code {"answer": "...",
 * "citations": [...]}}. El frontend dibujará el texto y el panel de
 * evidencias directamente de este JSON.
 */
@RestController
@RequestMapping("/api")
public class NovaController {

    private final NovaService nova;

    public NovaController(NovaService nova) {
        this.nova = nova;
    }

    /** Pregunta validada: sin texto no hay nada que recuperar (400 automático). */
    public record AskRequest(@NotBlank(message = "question es obligatoria") String question) {}

    @PostMapping("/ask")
    public ResponseEntity<NovaAnswer> ask(@Valid @RequestBody AskRequest request) {
        return ResponseEntity.ok(nova.ask(request.question()));
    }
}
