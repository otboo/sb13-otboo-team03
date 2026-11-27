package com.otboo.team03.recommendation.rag;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Shares public reference IDs only; no wardrobe text, user IDs or generated outfits. */
@Slf4j
public final class RedisOutfitReferenceCache {
    static final String PREFIX = "recommendation:references:v2:";
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final RecommendationRagProperties properties;

    public RedisOutfitReferenceCache(StringRedisTemplate redis, ObjectMapper mapper, RecommendationRagProperties properties) {
        this.redis = redis; this.mapper = mapper; this.properties = properties;
    }

    Optional<List<String>> find(String signature) {
        if (redis == null) return Optional.empty(); // Standalone indexing does not start Redis.
        try {
            String value = redis.opsForValue().get(PREFIX + signature);
            if (value == null || value.length() > properties.maxContextCharacters()) return Optional.empty();
            String[] ids = mapper.readValue(value, String[].class);
            if (ids == null || ids.length > properties.topK()
                    || Arrays.stream(ids).anyMatch(id -> id == null || id.isBlank())
                    || Arrays.stream(ids).distinct().count() != ids.length) return Optional.empty();
            return Optional.of(List.of(ids));
        } catch (DataAccessException | JacksonException exception) {
            log.debug("스타일 문맥 공유 캐시 조회 실패: errorType={}", exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    void save(String signature, List<StyleReference.Context> references) {
        if (redis == null) return;
        try {
            String value = mapper.writeValueAsString(references.stream().map(StyleReference.Context::id).toList());
            redis.opsForValue().set(PREFIX + signature, value, properties.cacheTtl());
        } catch (DataAccessException | JacksonException exception) {
            log.debug("스타일 문맥 공유 캐시 저장 실패: errorType={}", exception.getClass().getSimpleName());
        }
    }
}
