# ✨ NOVA — asistente RAG del portfolio (Hito 1)

Responde preguntas sobre el perfil y los proyectos de Javier usando **solo**
el contenido del portfolio, **siempre con citas**. Todo el código lleva
**comentarios explicativos en español**.

## Cómo funciona (hito 1: solo API, la cara llega en el hito 2)

```text
POST /api/ask {"question": "¿qué stack usa el EventDashboard?"}
   → RetrievalAugmentationAdvisor recupera los top-5 trozos del índice
   → Gemini responde SOLO con ese contexto (o dice que no lo sabe)
   → devolvemos {"answer": "...", "citations": [{project, title, excerpt}]}
```

**Ingesta** (una vez): los `.mdx` del portfolio → trozos → embeddings
`text-embedding-004` → `SimpleVectorStore` en un JSON. Cero infraestructura:
sin pgvector, sin Docker, sin servicios.

## Requisitos

- Java 17+
- **API key gratuita** de [Google AI Studio](https://aistudio.google.com/) (sin tarjeta),
  usada vía el endpoint compatible con OpenAI (decisión documentada en el pom
  y en incident-agent: el starter nativo `google-genai` solo existe en Spring AI 2.x).

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

## Tests (sin LLM)

```bash
./mvnw test
```

- `ProjectDocumentLoaderTest`: ingesta de .mdx (metadatos, sin frontmatter, tolerante a fallos).
- `NovaControllerTest`: contrato HTTP con servicio mockeado (MockMvc standalone, sin contexto Spring).

## Hoja de ruta

- **Hito 2**: cara de NOVA (SVG, 5 expresiones) ligada a los estados del chat.
- **Hito 3**: botón "pregúntale a NOVA" por proyecto + panel de evidencias.
- **Hito 4**: despliegue en portfolio-astro.
