package pro.javilesaca.nova.service;

import java.util.List;

/**
 * Respuesta de NOVA a una pregunta.
 *
 * <p>El record lleva la respuesta Y sus citas: el frontend muestra el texto y
 * el panel de evidencias sale de {@code citations} sin parsear nada.
 *
 * @param answer    texto en español generado SOLO con el contexto recuperado.
 * @param citations fuentes usadas, en orden de relevancia. Cada una identifica
 *                  el proyecto para enlazar a su ficha del portfolio.
 */
public record NovaAnswer(
        String answer,
        List<Citation> citations
) {
    /**
     * Una cita: qué proyecto la respalda y un extracto literal.
     * El extracto permite al usuario verificar sin "creer" al modelo.
     */
    public record Citation(String project, String title, String excerpt) {}
}
