# ✨ NOVA — laboratorio RAG del asistente del portfolio (Java + Spring AI)

NOVA responde preguntas sobre el perfil y los proyectos de Javier usando **solo**
el contenido del portfolio, **siempre con citas**. Si la respuesta no está en el
corpus, lo dice y no inventa. Esa es la idea entera del proyecto.

> **Qué es este repo:** el laboratorio de I+D del agente. Aquí se prototipa rápido
> (Java, tests, evals). La producción vive en
> [`nova-stellar`](https://github.com/javilesaca/nova-stellar) (PHP para el
> compartido de Namecheap) y la demo en el portfolio. Contrato único en ambos:
> `POST /api/ask → {answer, citations[]}`.

## Cómo funciona

```text
POST /api/ask {"question": "¿qué stack usa el EventDashboard?"}
   → RetrievalAugmentationAdvisor recupera los top-5 trozos del índice
   → Gemini responde SOLO con ese contexto (o dice que no lo sabe)
   → devolvemos {"answer": "...", "citations": [{project, title, excerpt}]}
```

**Ingesta** (una vez): los `.mdx` del portfolio → trozos → embeddings
`gemini-embedding-001` → `SimpleVectorStore` en un JSON. Cero infraestructura:
sin pgvector, sin Docker, sin servicios.

**Consola local** (`static/`): cara LED dot-matrix con 5 expresiones ligadas al
chat (`idle/thinking/speaking/happy/confused`) y retina que sigue al ratón.
La cara canónica vive en el portfolio; aquí está portada para probar el flujo.

## Decisiones (y lo descartado)

| Decisión | Elegido | Descartado | Por qué |
|---|---|---|---|
| Vector store | `SimpleVectorStore` en JSON | pgvector + Docker | 5 proyectos caben en un fichero; cero infra para un lab |
| Cliente Gemini | endpoint compatible OpenAI | starter nativo `google-genai` | solo existe en Spring AI 2.x; el pom lo documenta |
| Citas | las construye Java con los chunks | dejar que el modelo las invente | las fuentes nunca las genera el modelo |
| Runtime prod | PHP en compartido | JVM en VPS | el portfolio vive en Namecheap Stellar |

## Requisitos

- Java 17+
- **API key gratuita** de [Google AI Studio](https://aistudio.google.com/) (sin tarjeta).

## Puesta en marcha

```bash
GEMINI_API_KEY=tu-clave ./mvnw spring-boot:run   # NOVA en http://localhost:8091
```

La primera vez indexa (verás `[NOVA] Indexando...`); las siguientes reutiliza
el JSON. Para reindexar, borra el fichero `nova.store-file`.

Probar:

```bash
curl -X POST http://localhost:8091/api/ask \
  -H 'Content-Type: application/json' \
  -d '{"question":"¿Qué proyecto usa eventos en tiempo real?"}'
```

## Tests y eval dorado

```bash
./mvnw test
```

- `ProjectDocumentLoaderTest`: ingesta de .mdx (metadatos, sin frontmatter, tolerante a fallos).
- `NovaControllerTest`: contrato HTTP con servicio mockeado (sin contexto Spring).
- `GoldenSetTest`: valida el esquema de `evals/golden.json` sin red ni clave.
- `evals/golden.json`: 10 preguntas con proyectos esperados (+1 caso "no lo sé").
  Es el contrato de comportamiento: si cambias el prompt o el `topK` y un caso
  deja de recuperar su proyecto, el eval te avisa. La comprobación automática
  contra el índice vive en el roadmap (necesita clave de embeddings).

## Hoja de ruta

- **Hito 1** ✅ RAG con citas (este repo).
- **Hito 2** ✅ cara LED + consola local ([PR #1](https://github.com/javilesaca/nova/pull/1)).
- **Hito 3** 🔄 botón "pregúntale a NOVA" por proyecto + panel de evidencias (en `portfolio-astro`, [PR #2](https://github.com/javilesaca/portfolio-astro/pull/2)).
- **Hito 4** ⬜ despliegue en Namecheap Stellar (`nova-stellar`: rate limit, CORS, clave fuera del docroot).
- **Hito 5** ⬜ página de proyecto NOVA en el portfolio (caso de estudio con demo viva).

## Enlaces

- Producción: [`nova-stellar`](https://github.com/javilesaca/nova-stellar)
- Portfolio: botón flotante + (Hito 5) página de proyecto.
