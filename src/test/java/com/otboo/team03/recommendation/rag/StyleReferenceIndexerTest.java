package com.otboo.team03.recommendation.rag;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StyleReferenceIndexerTest {
    @Test void 모든_배치가_완료된_뒤에만_한번_원자적으로_저장한다() throws Exception {
        var catalog = RagTestFixtures.catalog();
        var embeddings = mock(OllamaEmbeddingClient.class); var store = mock(PgVectorReferenceStore.class);
        when(embeddings.embed(anyList())).thenAnswer(call -> Collections.nCopies(((List<?>) call.getArgument(0)).size(), RagTestFixtures.vector(0)));
        var properties = RagTestFixtures.properties(Map.of());
        assertThat(new StyleReferenceIndexer(catalog, embeddings, store, properties).rebuild()).isEqualTo(catalog.all().size());
        var order = inOrder(store);
        order.verify(store).initialize();
        order.verify(store).replace(eq(catalog.version()), eq(properties.modelKey()), eq(catalog.all()), argThat(vectors -> vectors.size() == catalog.all().size()));
    }
    @Test void 임베딩_중간에_실패하면_기존_인덱스를_건드리지_않는다() throws Exception {
        var embeddings = mock(OllamaEmbeddingClient.class); var store = mock(PgVectorReferenceStore.class);
        when(embeddings.embed(anyList())).thenReturn(Collections.nCopies(4, RagTestFixtures.vector(0)))
                .thenThrow(new ResourceAccessException("timeout"));
        var indexer = new StyleReferenceIndexer(RagTestFixtures.catalog(), embeddings, store, RagTestFixtures.properties(Map.of()));
        assertThatThrownBy(indexer::rebuild).isInstanceOf(ResourceAccessException.class);
        verifyNoInteractions(store);
    }
}
