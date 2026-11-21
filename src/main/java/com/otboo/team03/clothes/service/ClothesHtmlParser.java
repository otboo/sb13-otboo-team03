package com.otboo.team03.clothes.service;

import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import java.net.URI;
import java.util.List;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ClothesHtmlParser {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<String> PRODUCT_TYPES = List.of(
            "Product", "https://schema.org/Product", "http://schema.org/Product");

    public ExtractedClothes parse(Document document) {
        JsonNode product = structuredProduct(document);
        String type = meta(document, "og:type");
        if (product == null && !"product".equalsIgnoreCase(type) && !"og:product".equalsIgnoreCase(type)) {
            throw new OtbooException(ErrorCode.CLOTHES_EXTRACTION_FAILED);
        }
        String name = meta(document, "og:title");
        if (name.isBlank() && product != null) {
            name = product.path("name").asString("").strip();
        }
        if (name.isBlank()) {
            name = document.title().strip();
        }
        if (name.isBlank() || name.length() > 255) {
            throw new OtbooException(ErrorCode.CLOTHES_EXTRACTION_FAILED);
        }
        String image = imageUrl(document.location(), meta(document, "og:image"));
        if (image == null && product != null) {
            image = structuredImage(document.location(), product.path("image"));
        }
        return new ExtractedClothes(name, image);
    }

    private String meta(Document document, String property) {
        Element element = document.selectFirst("meta[property=" + property + "]");
        return element == null ? "" : element.attr("content").strip();
    }

    private JsonNode structuredProduct(Document document) {
        for (Element script : document.select("script[type=application/ld+json]")) {
            try {
                JsonNode product = findProduct(JSON.readTree(script.data()));
                if (product != null) return product;
            } catch (JacksonException ignored) {
                // 독립된 다음 JSON-LD 블록을 검사한다.
            }
        }
        return null;
    }

    private JsonNode findProduct(JsonNode node) {
        if (node == null) return null;
        if (node.isArray()) {
            for (JsonNode item : node) {
                JsonNode product = findProduct(item);
                if (product != null) return product;
            }
        }
        if (!node.isObject()) return null;
        JsonNode type = node.path("@type");
        if (PRODUCT_TYPES.contains(type.asString(""))) return node;
        for (JsonNode entry : type) {
            if (PRODUCT_TYPES.contains(entry.asString(""))) return node;
        }
        // 추천 목록의 Product는 현재 페이지의 상품이 아니다.
        JsonNode main = findProduct(node.get("mainEntity"));
        return main != null ? main : findProduct(node.get("@graph"));
    }

    private String structuredImage(String base, JsonNode node) {
        if (node.isArray()) {
            for (JsonNode item : node) {
                String image = structuredImage(base, item);
                if (image != null) return image;
            }
            return null;
        }
        String value = node.isObject() ? node.path("url").asString(node.path("contentUrl").asString(""))
                : node.asString("");
        return imageUrl(base, value);
    }

    private String imageUrl(String base, String value) {
        if (value.isBlank()) return null;
        try {
            URI uri = URI.create(base).resolve(value.strip());
            if (uri.getHost() != null && ("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme()))) return uri.toString();
        } catch (IllegalArgumentException ignored) {
            // 이미지 URL 오류는 상품명 추출을 막지 않는다.
        }
        return null;
    }

    public record ExtractedClothes(String name, String imageUrl) {}
}
