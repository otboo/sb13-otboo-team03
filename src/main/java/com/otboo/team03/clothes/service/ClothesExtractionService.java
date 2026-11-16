package com.otboo.team03.clothes.service;

import com.otboo.team03.clothes.dto.response.ClothesDto;
import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClothesExtractionService {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final ClothesPageClient pageClient;
    private final ClothesHtmlParser parser;

    /** 등록 전 초기값만 반환한다. DB 저장 및 외부 이미지 다운로드는 하지 않는다. */
    public ClothesDto extract(String url, UUID ownerId) {
        String html = pageClient.fetch(url);
        Document document = Jsoup.parse(html);
        var title = document.selectFirst("meta[property=og:title]");
        // 상품 경로라도 로그인/오류 HTML이 HTTP 200으로 내려올 수 있다.
        if (title == null || title.attr("content").isBlank() || !isProduct(document)) {
            throw new OtbooException(ErrorCode.CLOTHES_EXTRACTION_FAILED);
        }
        var extracted = parser.parse(html, url.strip());
        if (extracted.name().length() > 255) {
            throw new OtbooException(ErrorCode.CLOTHES_EXTRACTION_FAILED);
        }
        return new ClothesDto(null, ownerId, extracted.name(), extracted.imageUrl(), null, List.of());
    }

    private boolean isProduct(Document document) {
        var type = document.selectFirst("meta[property=og:type]");
        if (type != null && ("product".equalsIgnoreCase(type.attr("content").strip())
                || "og:product".equalsIgnoreCase(type.attr("content").strip()))) {
            return true;
        }
        for (var script : document.select("script[type=application/ld+json]")) {
            try {
                if (containsProduct(JSON.readTree(script.data()))) {
                    return true;
                }
            } catch (tools.jackson.core.JacksonException ignored) {
                // 잘못된 JSON-LD 블록은 건너뛰고 다음 블록을 확인한다.
            }
        }
        return false;
    }

    private boolean containsProduct(JsonNode node) {
        if (node == null) return false;
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (containsProduct(item)) return true;
            }
            return false;
        }
        if (!node.isObject()) return false;
        JsonNode type = node.get("@type");
        if (isProductType(type)) return true;
        if (type != null && type.isArray()) {
            for (JsonNode item : type) {
                if (isProductType(item)) return true;
            }
        }
        // 추천 상품(itemListElement 등)은 현재 페이지의 상품 근거로 사용하지 않는다.
        return containsProduct(node.get("@graph")) || containsProduct(node.get("mainEntity"));
    }

    private boolean isProductType(JsonNode type) {
        if (type == null || !type.isString()) return false;
        return List.of("Product", "https://schema.org/Product", "http://schema.org/Product")
                .contains(type.asString());
    }

}
