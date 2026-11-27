package com.otboo.team03.recommendation.rag;

import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.recommendation.client.dto.LlmRecommendationInput;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 명시된 충돌만 제외한다. 등록 속성·기장·정보 부족의 의미를 실험과 동일하게 유지한다. */
public final class ReferenceCompatibility {
    private static final Map<String, String> COLOR_WORDS = colorWords();
    private static final Map<String, List<String>> FIT_WORDS = Map.of(
            "compact", List.of("슬림", "컴팩트", "몸에 맞는", "타이트"),
            "relaxed", List.of("오버핏", "오버사이즈", "루즈핏", "루즈", "넉넉", "여유"),
            "wide", List.of("와이드", "통 넓", "배기"), "straight", List.of("스트레이트", "일자"),
            "regular", List.of("레귤러", "정핏"));
    public static final Set<String> COLORS = Set.copyOf(COLOR_WORDS.values());
    public static final Set<String> FITS = FIT_WORDS.keySet();
    private static final Pattern UNKNOWN = Pattern.compile(
            "아니|아님|아닌|않|불명|모름|미등록|미정|unknown|\\bnot\\b|\\bnon\\b|\\b없음\\b", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Set<String> COLOR_NAMES = Set.of("색상", "색", "컬러", "color", "colour");
    private static final Set<String> FIT_NAMES = Set.of("핏", "실루엣", "fit", "silhouette");

    public enum Assessment { CONFLICT, UNKNOWN, MATCH, UNCONSTRAINED }
    private record Evidence(ClothesType type, Set<String> colors, Set<String> fits) { }

    public Assessment assess(StyleReference reference, LlmRecommendationInput input) {
        List<Evidence> items = input.wardrobe().stream().filter(item -> !excluded(item.type()))
                .map(item -> new Evidence(item.type(), evidence(item, true), evidence(item, false))).toList();
        Map<ClothesType, Long> counts = items.stream().collect(Collectors.groupingBy(Evidence::type, Collectors.counting()));
        if (items.isEmpty() || (!reference.alternatives().isEmpty() && reference.alternatives().stream().noneMatch(
                roles -> roles.entrySet().stream().allMatch(e -> counts.getOrDefault(e.getKey(), 0L) >= e.getValue())))) {
            return Assessment.CONFLICT;
        }
        boolean unknown = false;
        boolean constrained = !reference.palette().isEmpty() || !reference.requirements().isEmpty();
        Set<String> observed = items.stream().flatMap(item -> item.colors().stream()).collect(Collectors.toSet());
        if (!observed.containsAll(reference.palette())) {
            if (items.stream().allMatch(item -> !item.colors().isEmpty())) return Assessment.CONFLICT;
            unknown = true;
        }
        for (var entry : reference.requirements().entrySet()) {
            var requirement = entry.getValue();
            List<Evidence> possible = items.stream().filter(item -> item.type() == entry.getKey())
                    .filter(item -> possible(item.colors(), requirement.colors()) && possible(item.fits(), requirement.fits())).toList();
            if (possible.isEmpty()) return Assessment.CONFLICT;
            if (possible.stream().noneMatch(item -> confirmed(item.colors(), requirement.colors())
                    && confirmed(item.fits(), requirement.fits()))) unknown = true;
        }
        if (!constrained) return Assessment.UNCONSTRAINED;
        return unknown ? Assessment.UNKNOWN : Assessment.MATCH;
    }
    private boolean possible(Set<String> actual, Set<String> allowed) {
        return allowed.isEmpty() || actual.isEmpty() || actual.stream().anyMatch(allowed::contains);
    }
    private boolean confirmed(Set<String> actual, Set<String> allowed) {
        return allowed.isEmpty() || (!actual.isEmpty() && allowed.containsAll(actual));
    }
    static boolean excluded(ClothesType type) { return type == ClothesType.UNDERWEAR || type == ClothesType.ETC; }
    private Set<String> evidence(LlmRecommendationInput.Item item, boolean color) {
        Set<String> names = color ? COLOR_NAMES : FIT_NAMES;
        List<String> explicit = item.attributes().stream()
                .filter(a -> names.contains(a.name().strip().toLowerCase(Locale.ROOT)) && !a.value().isBlank())
                .map(LlmRecommendationInput.Attribute::value).toList();
        String text = (explicit.isEmpty() ? item.name() : String.join(" ", explicit)).toLowerCase(Locale.ROOT);
        if (UNKNOWN.matcher(text).find()) return Set.of();
        if (color) return Arrays.stream(text.split("[\\s/·,()+_-]+"))
                .filter(COLOR_WORDS::containsKey).map(COLOR_WORDS::get).collect(Collectors.toSet());
        return FIT_WORDS.entrySet().stream().filter(e -> e.getValue().stream().anyMatch(text::contains))
                .map(Map.Entry::getKey).collect(Collectors.toSet());
    }
    private static Map<String, String> colorWords() {
        Map<String, String> result = new HashMap<>();
        Map<String, String> aliases = Map.ofEntries(
                Map.entry("brown", "brown 브라운 갈색 초콜릿 초콜릿색"), Map.entry("black", "black 블랙 검정 검정색 검은색"),
                Map.entry("white", "white 화이트 흰색 흰 백색"), Map.entry("grey", "gray grey 그레이 회색 헤더그레이"),
                Map.entry("blue", "blue 블루 파랑 파란색 하늘색 연하늘색 스카이블루 페일블루"),
                Map.entry("navy", "navy 네이비 남색"), Map.entry("cream", "cream 크림 크림색 아이보리 ivory"),
                Map.entry("taupe", "taupe 토프 회갈색"), Map.entry("green", "green 그린 초록 초록색 녹색 연두 연두색 라이트그린"),
                Map.entry("pink", "pink 핑크 분홍 분홍색"), Map.entry("orange", "orange 오렌지 주황 주황색"), Map.entry("beige", "beige 베이지"));
        aliases.forEach((canonical, words) -> Arrays.stream(words.split(" ")).forEach(word -> result.put(word, canonical)));
        return Map.copyOf(result);
    }
}
