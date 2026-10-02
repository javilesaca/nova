package pro.javilesaca.nova.store;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pro.javilesaca.nova.config.NovaProperties;
import pro.javilesaca.nova.ingest.ProjectDocumentLoader;

import java.io.File;
import java.nio.file.Path;

/**
 * Vector store EMBEBIDO: el índice vive en un fichero JSON, sin infra extra.
 *
 * <p>¿Por qué no pgvector/Qdrant? Para 5 ficheros sería matar moscas a
 * cañonazos: más piezas que operar, más coste, cero beneficio. SimpleVectorStore
 * carga el índice en memoria y basta de sobra. Si el corpus crece a miles de
 * documentos, se cambia esta clase y nada más (la interfaz VectorStore es la misma).
 *
 * <p>Estrategia de arranque: si existe el fichero, se carga (rápido, sin gastar
 * embeddings); si no, se indexa desde cero (lento la primera vez, una sola vez).
 */
@Configuration
public class VectorStoreConfig {

    @Bean
    SimpleVectorStore vectorStore(EmbeddingModel embeddingModel,
                                  ProjectDocumentLoader loader,
                                  NovaProperties properties) throws Exception {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        File file = Path.of(properties.storeFile()).toFile();
        if (file.exists()) {
            store.load(file);
            System.out.println("[NOVA] Índice cargado de " + file + ". Borra el fichero para reindexar.");
        } else {
            System.out.println("[NOVA] Indexando contenido de " + properties.contentDir() + " ...");
            store.add(loader.load());
            store.save(file);
            System.out.println("[NOVA] Índice guardado en " + file + ".");
        }
        return store;
    }
}
