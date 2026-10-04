# Hito 2 — Cara de NOVA (SVG, 5 expresiones)

## Objetivo
Darle cara a NOVA en `src/main/resources/static/index.html`: un SVG con 5 expresiones
ligadas a los estados del chat. Solo local, sin cambios de backend.

## Estados → expresiones
- `idle` — reposo (al cargar, antes de preguntar)
- `thinking` — pensando (fetch en curso, `…pensando…`)
- `answering` — respondiendo con éxito (respuesta + citas)
- `empty` — sin evidencia / no lo sabe (respuesta ok pero sin citas)
- `error` — error HTTP o de red

## Alcance
- Solo `static/`: `index.html` + `nova-face.js` + `nova-face.css` (o inline si es más simple).
- SVG hecho a mano, sin assets externos, accesible (`role=img`, `aria-label` por estado).
- Comentarios en español, como el resto del proyecto.

## No-objetivos
- Sin frameworks, sin cambios en Java.
- Sin Hito 3 (botón por proyecto) ni Hito 4 (despliegue).

## Verificación
- `./mvnw -q test` sigue en verde.
- Prueba manual: abrir `http://localhost:8091`, forzar cada estado y ver la expresión.
- Commits: work-unit en rama feature.

## Evidencia
- Worker mutlcons-1-1wpa: cara simple provisional (superada).
- Worker mutlmpdi-2-caks: porte canónico desde `portfolio-astro/src/components/NovaFace.astro` + `NovaWidget.astro`.
- `./mvnw -q test`: EXIT 0 (solo warnings Mockito/Hibernate).
- Servidor reiniciado 11:10 con el porte canónico: `Tomcat started on port 8091`, ROOT HTTP 200, índice cargado de /tmp/nova-vectors.json.
