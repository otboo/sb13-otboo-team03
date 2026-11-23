package com.otboo.team03.clothes.service;

import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
public class ClothesHtmlParser {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<String> PRODUCT_TYPES = List.of(
            "Product", "https://schema.org/Product", "http://schema.org/Product");

    public ExtractedClothes parse(Document document) {
        List<JsonNode> products = new ArrayList<>();
        JsonNode product = structuredProduct(document, products);
        String type = meta(document, "og:type");
        if (products.isEmpty() && !"product".equalsIgnoreCase(type) && !"og:product".equalsIgnoreCase(type)) {
            ExtractedClothes detail = shopDetail(document);
            if (detail != null) return detail;
            log.warn("Purchase page has no matching product: host={}", URI.create(document.location()).getHost());
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
        if (image == null) {
            ExtractedClothes detail = shopDetail(document);
            if (detail != null) image = detail.imageUrl();
        }
        return new ExtractedClothes(name, image);
    }

    private ExtractedClothes shopDetail(Document document) {
        URI page = URI.create(document.location());
        if (!page.getPath().matches("/products/[0-9]+")) return null;
        String id = page.getPath().substring("/products/".length());
        String title = document.title();
        if ("www.musinsa.com".equals(page.getHost())) {
            for (Element image : document.select("img[src][alt]")) {
                String src = imageUrl(document.location(), image.attr("src"));
                if (src == null) continue;
                URI uri = URI.create(src);
                String name = image.attr("alt").strip();
                if ("image.msscdn.net".equals(uri.getHost()) && uri.getPath().contains("/goods_img/")
                        && uri.getPath().contains("/" + id + "/") && !name.isBlank()
                        && name.length() <= 255 && title.contains(name)) {
                    return new ExtractedClothes(name, src);
                }
            }
        }
        if ("www.29cm.co.kr".equals(page.getHost()) && document.body().text()
                .matches("(?s).*상품번호[\\s\\p{Z}]*[:：]?[\\s\\p{Z}]*" + id + "(?![0-9]).*")) {
            String name = title.split(" - 모든 취향 곁에, 29CM", 2)[0].strip();
            if (!name.isBlank() && name.length() <= 255 && document.select("h1, h2").stream()
                    .anyMatch(heading -> heading.text().endsWith(name))) {
                return new ExtractedClothes(name, imageUrl(document.location(), meta(document, "og:image")));
            }
        }
        return null;
    }

    private String meta(Document document, String property) {
        Element element = document.selectFirst("meta[property=" + property + "]");
        return element == null ? "" : element.attr("content").strip();
    }

    private JsonNode structuredProduct(Document document, List<JsonNode> products) {
        Set<JsonNode> primary = new LinkedHashSet<>();
        Set<String> references = new LinkedHashSet<>();
        String base = document.location();
        for (Element script : document.select("script[type=application/ld+json]")) {
            try {
                collect(JSON.readTree(script.data()), base, products, primary, references, false);
            } catch (JacksonException ignored) {
                // 독립된 다음 JSON-LD 블록은 계속 검사한다.
            }
        }
        Set<JsonNode> matchingPage = new LinkedHashSet<>();
        for (JsonNode product : products) {
            String id = product.path("@id").asString("");
            if (references.contains(imageUrl(base, id))) primary.add(product);
            String url = imageUrl(base, product.path("url").asString(""));
            if (samePage(base, url) || (!id.contains("#") && samePage(base, imageUrl(base, id)))) matchingPage.add(product);
        }
        if (primary.size() == 1) return primary.iterator().next();
        if (!primary.isEmpty()) matchingPage.retainAll(primary);
        if (matchingPage.size() == 1) return matchingPage.iterator().next();
        return products.size() == 1 && references.isEmpty() ? products.get(0) : null;
    }

    private void collect(JsonNode node, String base, List<JsonNode> products,
                         Set<JsonNode> primary, Set<String> references, boolean mainEntity) {
        if (node == null) return;
        if (node.isArray()) {
            for (JsonNode entry : node) collect(entry, base, products, primary, references, mainEntity);
            return;
        }
        if (mainEntity) {
            String id = node.isString() ? node.asString() : node.path("@id").asString("");
            String ref = imageUrl(base, id);
            if (ref != null) references.add(ref);
        }
        if (!node.isObject()) return;
        if (isProduct(node)) {
            JsonNode urlNode = node.path("url");
            String url = urlNode.isObject() ? urlNode.path("@id").asString("") : urlNode.asString("");
            if (!url.isBlank() && !samePage(base, imageUrl(base, url))) return;
            products.add(node);
            if (mainEntity) primary.add(node);
            return;
        }
        collect(node.get("mainEntity"), base, products, primary, references, true);
        collect(node.get("@graph"), base, products, primary, references, false);
    }

    private boolean isProduct(JsonNode node) {
        JsonNode type = node.path("@type");
        if (PRODUCT_TYPES.contains(type.asString(""))) return true;
        for (JsonNode entry : type) {
            if (PRODUCT_TYPES.contains(entry.asString(""))) return true;
        }
        return false;
    }

    private boolean samePage(String base, String url) {
        if (url == null) return false;
        URI candidate = URI.create(url);
        URI page = URI.create(base);
        return page.getHost().equalsIgnoreCase(candidate.getHost())
                && page.getScheme().equalsIgnoreCase(candidate.getScheme()) && httpsPort(page) == httpsPort(candidate)
                && page.getPath().replaceFirst("/$", "").equals(candidate.getPath().replaceFirst("/$", ""));
    }

    private int httpsPort(URI uri) {
        return uri.getPort() == -1 ? 443 : uri.getPort();
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
