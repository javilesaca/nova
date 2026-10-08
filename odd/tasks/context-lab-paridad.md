# Soporte context en lab (paridad con PHP)

## Objetivo
`POST /api/ask` acepta `{question, context?}` y filtra por proyecto cuando `context` está en whitelist, igual que `../nova-stellar/api/ask.php`.

## No-objetivos
- Sin tocar `../nova-stellar` ni `../portfolio-astro`.
- Sin commits ni push.
- Sin cambios de cara LED ni estilos.

## Superficies permitidas
- `src/main/java/pro/javilesaca/nova/controller/NovaController.java`
- `src/main/java/pro/javilesaca/nova/service/NovaService.java`
- `src/test/java/pro/javilesaca/nova/controller/NovaControllerTest.java`
- `src/test/java/pro/javilesaca/nova/service/*` (solo si hace falta nuevo test)

## Tareas
1. `AskRequest` con `context` opcional + `ignoreUnknown=true`, pasar a servicio.
2. `NovaService.ask(question, context)` filtra por metadato `project` si `context` en whitelist `['agente-gamer','event-dashboard','ranking-videojuegos','hockey-pong','memory-cards']`; si no, búsqueda global como ahora. Mantener compat `ask(String)` delegando con null.
3. Tests: `question` solo → 200 global; con `context` válido → filtra; con `context` inválido → global; `question` vacía → 400. `./mvnw -q test` verde.

## Evidencia
- Writer muz8p69q: 3 ficheros, +83/-5. `./mvnw test` → 10 tests, 0 fallos, BUILD SUCCESS.
- Verificador muz8qn6d: `./mvnw -q test` EXIT 0 (Controller 5, Golden 1, Loader 4). Sin-context 200, forward válido, inválido → global, extras tolerados, vacía → 400.
- Riesgos: answer del modelo sale de advisor global (solo citas filtradas); edges `trim/case` no normalizados (PHP hace trim, Java no). Sin cobertura directa de `NovaService.ask(q,ctx)`.
- Sin commits. Listo para prueba local con GEMINI_API_KEY.
- Decisión 2026-10-08: opción 1 (paridad de evidencias). E2E local mostró citas [] con context genérico; se documenta como comportamiento esperado. Mejora de filtrar contexto del modelo queda para Hito 5.
