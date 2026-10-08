# Ahorro de llamadas Gemini + 429 amable (núcleo genérico)

## Por qué
2026-10-08: posible cuota gratuita excedida (todos los LLM-path dan 500).
Cada pregunta cuesta 2 embeddings + 1 chat. Nada de esta tarea nombra a ningún
dueño: textos genéricos, identidad sigue viniendo de `nova.owner.*`.

## Alcance
1. `NovaService`: RAG manual con UNA sola recuperación.
   - `similaritySearch` una vez → mismos docs para (a) contexto del prompt del
     modelo y (b) citas. Se elimina el `RetrievalAugmentationAdvisor` o se deja
     sin retrieval propio si es trivial; lo simple manda: prompt con contexto
     inyectado a mano + `chatClient` sin advisor.
   - Mantener: fast-paths, IDENTIDAD, filtro `context`, compat `ask(String)`,
     system prompt compuesto, topK/threshold de properties.
2. Degradación amable y genérica: cualquier excepción de embedding/chat
   (429, 5xx, red) → `NovaAnswer("El servicio de IA está saturado o sin cuota. Prueba de nuevo más tarde.", [])`, HTTP 200, sin 500. Log WARN con la causa (sin secretos).
3. Caché de respuestas repetidas: mapa pregunta-normalizada → NovaAnswer con TTL
   corto (p. ej. 10 min, tamaño acotado LRU ~100). Solo para respuestas del LLM
   (no fast-paths, que ya son gratis). Hilo-seguro.
4. Front `index.html`: si `answer` contiene ese texto (o citas vacías + flag),
   cara `confused`. Sin literales de ningún dueño.
5. Tests: una sola recuperación por pregunta (verify times(1) en vectorStore),
   fallo del modelo → canned amable + 200, segunda pregunta idéntica sale de caché
   (sin nueva llamada al modelo), flujos existentes intactos.
   `./mvnw -q test` verde.

## No-objetivos
- Sin tocar `../nova-stellar` ni `../portfolio-astro`.
- Sin commits ni push. Sin timeout 15 s ni whitelist.
- Sin datos de ningún individuo en código ni tests (usar "Acme"/genéricos).

## Superficies permitidas
- `src/main/java/pro/javilesaca/nova/service/NovaService.java`
- `src/main/resources/static/index.html`
- `src/test/java/pro/javilesaca/nova/service/NovaServiceTest.java`
- `src/test/java/pro/javilesaca/nova/controller/NovaControllerTest.java`

## Verificación
- `./mvnw -q test` verde.
- Manual con cuota: pregunta de proyecto responde o dice «saturado» sin 500.

## Evidencia
- Writer muzcfomh: RAG manual 1 retrieval, degradación amable, caché LRU/TTL, front SATURADO. `./mvnw test` → 23 tests, 0 fallos, BUILD SUCCESS.
- Verificador muzchki4: `./mvnw -q test` EXIT 0 (Service 12, Controller 6, Loader 4, Golden 1). Sin advisor, fallo no cacheado, 200 ante saturación.
- Ahorro: 2 emb+1 chat → 1 emb+1 chat (~50% embedding); repetida en 10 min → 0 llamadas.
- Riesgos: prompt manual sin filtros del retriever; caché obsoleta ante cambio de docs en TTL.
- Sin commits. Pendiente merge junto con identidad-y-guia.
