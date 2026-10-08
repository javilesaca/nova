# Identidad configurable + guía con preguntas (núcleo genérico)

## Principio
NOVA es un agente genérico reutilizable en varios sitios. La identidad del dueño
NO se hardcodea en el servicio: se inyecta por configuración y contenido.
El lab `nova` la configura con Javier; otro despliegue configura la suya sin tocar código.

## Alcance
1. `NovaProperties`: nuevo grupo `nova.owner.*` con `name`, `role`, `bio`, `areas` (lista),
   `hire` (por qué contratarle) y `chips` (preguntas sugeridas). Todo opcional con
   valores por defecto vacíos; si está vacío, el comportamiento es el actual (sin identidad).
   En `application.properties` del lab, valores de Javier (bio del portfolio, sin secretos).
2. `NovaService`: bloque IDENTIDAD genérico antes del fast-path actual.
   - Detecta (normalizado): `quien eres`, `quien es <owner.name>`, `experiencia`,
     `por que contratar`, `a que te dedicas`, `sobre ti`.
   - Responde componiendo `owner` configurado (quién es / experiencia / por qué contratarle)
     + `citations: [perfil]`. Si `owner.name` está vacío, no intercepta (pasa al flujo normal).
   - SYSTEM_PROMPT se compone con `owner.name/role/areas` cuando existen.
3. Consola `static/index.html`: fila de chips leída de config idealmente; como el estático
   no lee properties, duplica las 3 de prod como HTML (`¿Qué experiencia tiene?`,
   `¿Qué proyectos usan IA?`, `¿Por qué contratarle?`) con comentario de paridad con
   `NovaWidget.astro` de portfolio-astro. Un clic = rellenar y enviar.
4. Tests: identidad configurada responde canned sin LLM; sin identidad no intercepta;
   portfolio y general intactos. `./mvnw -q test` verde.

## No-objetivos
- Sin tocar `../nova-stellar` ni `../portfolio-astro`.
- Sin commits ni push.
- Sin cambiar timeout 15 s, whitelist de `context` ni fast-path de tiempo/ayuda.

## Superficies permitidas
- `src/main/java/pro/javilesaca/nova/config/NovaProperties.java`
- `src/main/java/pro/javilesaca/nova/service/NovaService.java`
- `src/main/resources/application.properties`
- `src/main/resources/static/index.html`
- `src/test/java/pro/javilesaca/nova/service/NovaServiceTest.java`

## Verificación
- `./mvnw -q test` verde.
- Manual: `curl` «¿quién es Javier Lesaca?» responde bio en <2 s; chips en `:8091` envían al clic;
  con `owner` vacío no intercepta.

## Evidencia
- Writer muzavmkm: 5 ficheros, +160/-6. `./mvnw test` → 18 tests, 0 fallos, BUILD SUCCESS.
- Verificador muzay22b: `./mvnw -q test` EXIT 0 (Controller 5, Golden 1, Loader 4, Service 8). Genericidad OK, binding List OK, chips OK.
- Deudas: canned WEATHER/GENERAL aún nombran a Javier en literal; falsos positivos identidad (`experiencia` en pregunta de proyecto); chips duplicados en HTML vs config.
- README.md reescrito (contrato, config owner.*, hitos al día, 18 tests). Sin commits.
