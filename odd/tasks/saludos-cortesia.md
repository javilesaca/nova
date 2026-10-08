# Saludos y cortesía al fast-path, acotados (núcleo genérico)

## Por qué
2026-10-08 en local: «hola nova» iba al modelo (42 s+ o 500 sin cuota).
Los saludos deben responder al instante sin LLM.

## Alcance
1. `GENERAL_TOKENS` + `SALUDO_TOKENS` (hola, buenas, dias, gracias, adiós...).
   Canned solo en preguntas cortas (`SALUDO_MAX_TOKENS` / `GENERAL_MAX_TOKENS` = 4):
   «hola nova» → canned; «Hola, cuéntame del proyecto ranking» → RAG;
   «¿qué proyecto ayuda a gestionar gastos?» → RAG.
2. Orden intacto: identidad → weather (con guarda «tiempo real») → general → saludo.
3. Tests en `NovaServiceTest` para cortos, largas con saludo/ayuda y existentes.

## No-objetivos
- Sin dueño en código ni tests. Sin commits ni push.

## Evidencia
- Writers + verifiers: 24 → 25 → 26 tests verde; unificación del 4 en constantes.
- Sin commits. Se mergea con esta rama.
