package com.otboo.team03.recommendation.rag;

import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.clothes.event.WardrobeChangedEvent;
import com.otboo.team03.recommendation.config.RecommendationLlmProperties;
import com.otboo.team03.recommendation.service.RecommendationInputReader;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@ConditionalOnProperty(name = "otboo.recommendation.llm.enabled", havingValue = "true")
public class WardrobeReferenceWarmup {
    private final RecommendationInputReader reader;
    private final OutfitReferenceProvider references;
    private final RecommendationLlmProperties llm;
    private final ObjectMapper mapper;
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), runnable -> {
                Thread thread = new Thread(runnable, "wardrobe-reference-warmup");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    public WardrobeReferenceWarmup(RecommendationInputReader reader, OutfitReferenceProvider references,
            RecommendationLlmProperties llm, ObjectMapper mapper) {
        this.reader = reader; this.references = references; this.llm = llm; this.mapper = mapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChanged(WardrobeChangedEvent event) {
        try {
            worker.execute(() -> {
                try {
                    var candidate = reader.readForStyleReferences(event.ownerId(), llm.maxClothes());
                    if (candidate.isEmpty()) return;
                    var input = candidate.get();
                    var types = input.wardrobe().stream().map(item -> item.type()).toList();
                    if (!(types.contains(ClothesType.DRESS) || types.contains(ClothesType.TOP) && types.contains(ClothesType.BOTTOM))
                            || mapper.writeValueAsString(input).length() > llm.maxInputCharacters()) return;
                    references.prepare(input);
                } catch (RuntimeException exception) {
                    // Optional preparation must never change the committed CRUD result.
                    log.warn("스타일 사전 검색 준비 실패: errorType={}", exception.getClass().getSimpleName());
                }
            });
        } catch (RejectedExecutionException exception) {
            log.debug("스타일 사전 검색 생략: 종료 또는 읽기 작업 한도 초과");
        }
    }

    @PreDestroy
    void close() { worker.shutdownNow(); }
}
