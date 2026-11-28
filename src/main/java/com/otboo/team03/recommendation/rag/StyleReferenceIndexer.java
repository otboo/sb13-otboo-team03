package com.otboo.team03.recommendation.rag;

import java.util.ArrayList;
import java.util.List;

/** 명시적인 인덱싱 명령에서만 호출한다. 추천·앱 시작 시 임베딩을 다시 만들지 않는다. */
public final class StyleReferenceIndexer {
    private final StyleReferenceCatalog catalog;
    private final OllamaEmbeddingClient embeddings;
    private final PgVectorReferenceStore store;
    private final RecommendationRagProperties properties;

    public StyleReferenceIndexer(StyleReferenceCatalog catalog, OllamaEmbeddingClient embeddings,
            PgVectorReferenceStore store, RecommendationRagProperties properties) {
        this.catalog = catalog;
        this.embeddings = embeddings;
        this.store = store;
        this.properties = properties;
    }
    public int rebuild() {
        List<StyleReference> references = catalog.all();
        List<float[]> vectors = new ArrayList<>();
        for (int offset = 0; offset < references.size(); offset += 4) {
            vectors.addAll(embeddings.embed(references.subList(offset, Math.min(offset + 4, references.size()))
                    .stream().map(StyleReference::embeddingText).toList()));
        }
        // Complete model responses precede an atomic database replacement.
        store.initialize();
        store.replace(catalog.version(), properties.modelKey(), references, vectors);
        return references.size();
    }
}
