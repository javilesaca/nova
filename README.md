# ✨ NOVA — agente RAG genérico (lab Java + Spring AI)

NOVA responde preguntas sobre un portfolio usando **solo** su contenido,
**siempre con citas**. Si no lo sabe, lo dice y no inventa.

> **Núcleo genérico, identidad inyectable.** El servicio no hardcodea ningún
> dueño: la identidad (`nova.owner.*`) entra por configuración y contenido.
> Este repo es el **laboratorio** configurado con Javier; la producción vive en
> [`nova-stellar`](https://github.com/javilesaca/nova-stellar) (PHP para el
> compartido de Namecheap) y la demo en el portfolio. Contrato único:
> `POST /api/ask → {answer, citations[]}`.

## Cómo funciona

```text
POST /api/ask {"question": "...", "context": "event-dashboard"}
   → 0. IDENTIDAD: ¿preguntan por el dueño? → bio configurada, sin LLM (<2 s)
   → 1. FAST-PATH: ¿fuera de ámbito (tiempo, chistes, ayuda)? → canned, sin LLM (<2 s)
   → 2. RAG: RetrievalAugmentationAdvisor recupera top-K trozos del índice
   → 3. Gemini responde SOLO con ese contexto (o dice que no lo sabe)
   → 4. Java filtra citas por `context` (whitelist) y devuelve {answer, citations[]}
```

**Ingesta** (una vez): los `.mdx` del portfolio → trozos → embeddings
`gemini-embedding-001` → `SimpleVectorStore` en un JSON. Cero infraestructura:
sin pgvector, sin Docker, sin servicios.

**`context` (paridad PHP):** el frontend envía el slug del proyecto en contexto;
si está en la whitelist, las citas se filtran por `project`. Cualquier otro
valor → búsqueda global.

**Consola local** (`static/`): cara LED dot-matrix con 5 expresiones ligadas al
chat (`idle/thinking/speaking/happy/confused`), retina que sigue al ratón,
chips de preguntas sugeridas (paridad con el widget del portfolio) y timeout
de 15 s con aviso (nunca `thinking` eterno).

## Decisiones (y lo descartado)

| Decisión | Elegido | Descartado | Por qué |
| --- | --- | --- | --- |
| Vector store | `SimpleVectorStore` en JSON | pgvector + Docker | 5 proyectos caben en un fichero; cero infra para un lab |
| Cliente Gemini | endpoint compatible OpenAI | starter nativo `google-genai` | solo existe en Spring AI 2.x; el pom lo documenta |
| Citas | las construye Java con los chunks | dejar que el modelo las invente | las fuentes nunca las genera el modelo |
| Runtime prod | PHP en compartido | JVM en VPS | el portfolio vive en Namecheap Stellar |
| Identidad | `nova.owner.*` por configuración | bio hardcodeada en el servicio | el agente se reutiliza en varios sitios sin tocar código |
| Fuera de ámbito | fast-path canned sin LLM | todo al modelo | 40-90 s por pregunta; el canned responde en ms |

## Requisitos

- Java 21, Maven wrapper incluido (`./mvnw`, sin instalación).
- **API key gratuita** de [Google AI Studio](https://aistudio.google.com/) (sin tarjeta),
  solo en entorno local: `export GEMINI_API_KEY=...` (nunca en ficheros).

## Puesta en marcha

```bash
export GEMINI_API_KEY=tu-clave
./mvnw spring-boot:run   # NOVA en http://localhost:8091
```

La primera vez indexa; las siguientes reutiliza el JSON de `nova.store-file`.
Para reindexar, borra ese fichero. Para probar otro contenido:
`NOVA_CONTENT_DIR=/ruta/a/otros/mdx`.

```bash
curl -X POST http://localhost:8091/api/ask \
  -H 'Content-Type: application/json' \
  -d '{"question":"¿Qué proyecto usa eventos en tiempo real?"}'

curl -X POST http://localhost:8091/api/ask \
  -H 'Content-Type: application/json' \
  -d '{"question":"¿Quién es Javier Lesaca?"}'          # identidad, <2 s

curl -X POST http://localhost:8091/api/ask \
  -H 'Content-Type: application/json' \
  -d '{"question":"¿Qué tiempo hace hoy?"}'             # fuera de ámbito, <2 s
```

## Configuración (`nova.*`)

| Clave | Ejemplo del lab | Efecto |
| --- | --- | --- |
| `nova.content-dir` | `.../portfolio-astro/src/content/projects/es` | `.mdx` a indexar |
| `nova.store-file` | `/tmp/nova-vectors.json` | índice persistido |
| `nova.top-k` | `5` | trozos por pregunta |
| `nova.owner.name/role/bio/areas/hire` | Javier Lesaca Medina, IT Infrastructure & … | identidad del dueño |
| `nova.owner.chips` | 3 preguntas sugeridas | guía (paridad widget) |

Sin `owner`, el agente funciona igual pero sin bloque de identidad.
Deuda conocida: los canned de tiempo/ayuda aún nombran a Javier en literal
(ver `odd/tasks/identidad-y-guia.md`); el saneamiento total queda pendiente.

## Tests y eval dorado

```bash
./mvnw test   # 18 tests en verde
```

- `ProjectDocumentLoaderTest` (4): ingesta de .mdx (metadatos, sin frontmatter, tolerante a fallos).
- `NovaControllerTest` (5): contrato HTTP con servicio mockeado + `context`.
- `NovaServiceTest` (8): fast-path fuera de ámbito + identidad configurable sin LLM.
- `GoldenSetTest` (1): valida el esquema de `evals/golden.json` sin red ni clave.
- `evals/golden.json`: preguntas con proyectos esperados (+ caso "no lo sé").
  La comprobación automática contra el índice vive en el roadmap (necesita clave de embeddings).

## Mapa

- `nova` (este repo) = laboratorio genérico + config de Javier.
- `nova-stellar` = producto individual (PHP, rate limit 20/min, CORS restringido, FAQ sin LLM).
- `portfolio-astro` = consume y exhibe (widget, CTA por proyecto, cara canónica).

## Hoja de ruta

- **Hito 1** ✅ RAG con citas.
- **Hito 2** ✅ cara LED + consola local ([PR #1](https://github.com/javilesaca/nova/pull/1)).
- **Hito 3** ✅ botón "pregúntale a NOVA" por proyecto + panel de evidencias ([PR #2](https://github.com/javilesaca/portfolio-astro/pull/2)).
- Pulidos ✅ `context` con whitelist · fast-path fuera de ámbito · timeout 15 s · identidad configurable + chips.
- **Hito 4** 🔄 despliegue en Namecheap Stellar (`nova-stellar` abierto, sin mergear).
- **Hito 5** ⬜ página de proyecto NOVA en el portfolio (caso de estudio con demo viva).

## Enlaces

- Producción: [`nova-stellar`](https://github.com/javilesaca/nova-stellar)
- Portfolio: https://javilesaca.pro (botón flotante + Hito 5: página de proyecto).
