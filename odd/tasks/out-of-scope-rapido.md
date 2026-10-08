# Respuesta rápida fuera de ámbito + timeout front

## Objetivo
Preguntas fuera del portfolio (tiempo, fecha, chistes, ayuda general) responden rápido sin esperar 40-60 s a Gemini, con frases tipo «No es mi función» / «No estoy entrenado para esa tarea». El front deja de quedarse en `thinking` eterno con timeout de 15 s → cara `confused`.

## Por qué
E2E local 2026-10-08: `Hola` tarda 42 s, pregunta del tiempo >60 s sin respuesta. Cada `ask` cuesta 2 embeddings + 1 chat. El `fetch` de la consola no tiene timeout.

## Alcance
1. Backend `NovaService`: fast-path sin LLM para fuera de ámbito (lista de patrones: tiempo/clima, hora/fecha, chistes, quién eres genérico no-portfolio, ayuda general). Devuelve `NovaAnswer` con texto canónico en español + `citations: []` sin llamar al modelo ni al vector store.
   - Textos: si preguntan por tiempo/clima/hora → «No es mi función: solo respondo sobre el portfolio de Javier y sus proyectos.»; resto fuera de ámbito → «No estoy entrenado para esa tarea: respondo solo sobre el perfil y los proyectos de Javier. Puedo contarte sobre Ranking de Videojuegos, Hockey Pong o Memory Card.»
   - Mantener `ask(q, ctx)` y whitelist existentes; el fast-path va antes de todo.
2. Front `static/index.html`: `fetch` con `AbortController` timeout 15 s → `pending.textContent` avisa del tiempo agotado + cara `confused`. Sin librerías.
3. Tests: unit del fast-path (tiempo → canned sin mock LLM necesario o con mock; global intacto) + contrato 200 con `citations: []`. `./mvnw -q test` verde.

## No-objetivos
- Sin tocar `../nova-stellar` ni `../portfolio-astro`.
- Sin commits ni push.
- Sin cambiar el advisor/retriever global ni el filtro de `context`.

## Superficies permitidas
- `src/main/java/pro/javilesaca/nova/service/NovaService.java`
- `src/main/resources/static/index.html`
- `src/test/java/pro/javilesaca/nova/service/NovaServiceTest.java` (nuevo si hace falta)
- `src/test/java/pro/javilesaca/nova/controller/NovaControllerTest.java` (solo ajustes de mock si hace falta)

## Verificación
- `./mvnw -q test` verde.
- Manual: `curl` pregunta del tiempo responde en <2 s con canned + `citations: []`; `Hola` portfolio sigue global; en navegador, pregunta lenta >15 s muestra timeout y cara `confused`.

## Evidencia
- Writer muza7sur: NovaService +36, index.html +28/-7, nuevo NovaServiceTest (5 tests). `./mvnw test` → 15 tests, 0 fallos, BUILD SUCCESS.
- Verificador muzaba84: `./mvnw -q test` EXIT 0 (Controller 5, Golden 1, Loader 4, Service 5). Fast-path antes de LLM, guarda «tiempo real», timeout 15 s sin restos 30 s.
- Riesgos: falsos positivos por token (`clima laboral`, `hora`/`hoy` colaterales); sin E2E con LLM real.
- Sin commits. Para mergear en local cuando autorices.
