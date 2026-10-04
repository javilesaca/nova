# Hito 3 — Botón "pregúntale a NOVA" por proyecto + panel de evidencias

## Objetivo
Cada página de proyecto del portfolio ofrece preguntar a NOVA sobre ESE proyecto:
abre el dock con la pregunta precargada (y enviada) con el slug como contexto,
y la respuesta llega con su panel de Fuentes. En 6 idiomas.

## Por qué es solo frontend
- `api/ask.php` ya acepta `{question, context}` (slug con whitelist) y devuelve `{answer, citations[]}`.
- `NovaWidget` ya envía `context`, ya pinta `.nova__sources` y ya escucha `nova:open` (ignora `detail`).
- Falta: CTA por proyecto + soporte de `detail` en el widget.

## Alcance (todo en portfolio-astro, nada de backend)
1. Nuevo `src/components/NovaProjectCTA.astro` (props: `slug`, `title`, `label`, `question`):
   dispara `document.dispatchEvent(new CustomEvent('nova:open', {detail: {project: slug, question}}))`.
2. Incluirlo en las 6 plantillas `src/pages/{proyectos,de,en,fr,it,pt/...}/[slug].astro`
   (cada una pasa sus strings en su idioma, como ya hacen con los headings).
3. `NovaWidget`: el listener de `nova:open` acepta `detail {project?, question?}`:
   fija `lastProject`, precarga el input y hace `requestSubmit()` (igual que los chips).
4. Pregunta sugerida por idioma sobre el título del proyecto (p. ej. es: `¿Qué hace el proyecto X?`).

## No-objetivos
- Sin cambios en `nova` (Java) ni `nova-stellar` (PHP).
- Sin página de proyecto NOVA (Hito 5).
- Sin nuevos estilos globales: reutilizar los del dock.

## Verificación
- `npm run build` en portfolio-astro en verde.
- Manual: CTA en es + en abre el dock, pregunta el proyecto correcto y muestra Fuentes.
- `PUBLIC_NOVA_ENDPOINT` apuntando al backend local para la prueba.

## Evidencia
- `NovaProjectCTA.astro` nuevo + `NovaWidget` con `detail` + 6 plantillas (implementado inline:
  el writer solo admite rutas dentro de `nova` y el Hito 3 vive en `portfolio-astro`).
- Verificador mutmvjcm-4-c3kx: `npm run build` éxito (36 páginas, sin errores).
- Pendiente: clic real en navegador (es/en) contra backend local.
