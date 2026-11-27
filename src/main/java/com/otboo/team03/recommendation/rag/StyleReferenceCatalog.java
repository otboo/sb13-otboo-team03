package com.otboo.team03.recommendation.rag;

import com.otboo.team03.clothes.entity.ClothesType;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.core.io.Resource;

public final class StyleReferenceCatalog {
    private final Map<String, StyleReference> references;
    private final String version;

    public StyleReferenceCatalog(Resource resource) throws IOException {
        byte[] bytes = resource.getContentAsByteArray();
        try { version = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
        List<String> lines = new String(bytes, StandardCharsets.UTF_8).lines().toList();
        if (lines.size() < 2) throw new IllegalArgumentException("스타일 자료가 비어 있습니다.");
        List<String> headers = Arrays.asList(lines.get(0).split("\t", -1));
        Map<String, StyleReference> loaded = new LinkedHashMap<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] values = line.split("\t", -1);
            if (values.length != headers.size()) throw new IllegalArgumentException("스타일 자료 열 수 오류");
            Function<String, String> get = key -> {
                int index = headers.indexOf(key);
                if (index < 0) throw new IllegalArgumentException("스타일 자료 필수 열 누락: " + key);
                return values[index];
            };
            String id = get.apply("id"), summary = get.apply("summary");
            URI url = URI.create(get.apply("url"));
            if (id.isBlank() || summary.isBlank() || summary.length() > 500
                    || !"https".equals(url.getScheme()) || url.getHost() == null || get.apply("source").isBlank()) {
                throw new IllegalArgumentException("스타일 자료 ID·요약·출처 오류");
            }
            Map<ClothesType, StyleReference.Requirement> requirements = new EnumMap<>(ClothesType.class);
            for (ClothesType type : List.of(ClothesType.TOP, ClothesType.BOTTOM, ClothesType.OUTER)) {
                var colors = tokens(get.apply(type + "_color"));
                var fits = tokens(get.apply(type + "_fit"));
                if (!ReferenceCompatibility.COLORS.containsAll(colors) || !ReferenceCompatibility.FITS.containsAll(fits)) {
                    throw new IllegalArgumentException("알 수 없는 색상·핏 제약");
                }
                if (!colors.isEmpty() || !fits.isEmpty()) requirements.put(type, new StyleReference.Requirement(colors, fits));
            }
            Set<String> palette = tokens(get.apply("palette"));
            if (!ReferenceCompatibility.COLORS.containsAll(palette)) throw new IllegalArgumentException("배색 제약 오류");
            String mode = get.apply("mode"), roles = get.apply("required_types");
            if (!Set.of("combination", "palette", "layer", "accent").contains(mode)
                    || roles.equals("*") != mode.equals("palette") || get.apply("basis").isBlank()) {
                throw new IllegalArgumentException("자료 적용 범위 오류");
            }
            var reference = new StyleReference(id, get.apply("source"), url.toString(), get.apply("source_season"),
                    get.apply("tags"), summary, palette, requirements, parseRoles(roles));
            if (loaded.putIfAbsent(id, reference) != null) throw new IllegalArgumentException("중복 자료 ID");
        }
        references = java.util.Collections.unmodifiableMap(loaded);
    }
    static List<Map<ClothesType, Long>> parseRoles(String expression) {
        if (expression.equals("*")) return List.of();
        List<Map<ClothesType, Long>> alternatives = new ArrayList<>();
        for (String alternative : expression.split("\\|", -1)) {
            List<ClothesType> types = Arrays.stream(alternative.split("\\+", -1)).map(ClothesType::valueOf).toList();
            if (types.stream().anyMatch(ReferenceCompatibility::excluded)) throw new IllegalArgumentException("추천 제외 종류");
            alternatives.add(types.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting())));
        }
        return List.copyOf(alternatives);
    }
    private static Set<String> tokens(String value) {
        return Arrays.stream(value.split(",")).filter(token -> !token.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }
    public String version() { return version; }
    public List<StyleReference> all() { return List.copyOf(references.values()); }
    public StyleReference get(String id) { return references.get(id); }
}
