package com.otboo.team03.recommendation.rag;

import com.otboo.team03.clothes.entity.ClothesType;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 출처가 있는 짧은 요약과 수작업으로 확인한 적용 범위. 외부 의상 ID는 보관하지 않는다. */
public record StyleReference(String id, String source, String url, String sourceSeason, String tags,
                             String summary, Set<String> palette,
                             Map<ClothesType, Requirement> requirements,
                             List<Map<ClothesType, Long>> alternatives) {
    public StyleReference {
        palette = Set.copyOf(palette);
        requirements = Map.copyOf(requirements);
        alternatives = alternatives.stream().map(Map::copyOf).toList();
    }
    public record Requirement(Set<String> colors, Set<String> fits) {
        public Requirement { colors = Set.copyOf(colors); fits = Set.copyOf(fits); }
    }
    public String embeddingText() { return sourceSeason + " / " + tags + " / " + summary; }
    public record Context(String id, String source, String url, String summary, String compatibility) { }
}
