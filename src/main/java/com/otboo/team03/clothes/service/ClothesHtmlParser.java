package com.otboo.team03.clothes.service;

import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class ClothesHtmlParser {

    /**
     * 전달받은 HTML에서 상품명과 대표 이미지를 추출한다.
     * pageUrl은 상대 이미지 경로를 절대 URL로 변환하는 기준이다.
     */
    public ExtractedClothes parse(String html, String pageUrl) {
        Document document = Jsoup.parse(html, pageUrl);

        // Open Graph 제목을 우선 사용하고, 없으면 HTML 제목을 사용한다.
        String name = readMeta(document, "meta[property=og:title]");

        if (name == null) {
            name = document.title().strip();
        }

        // 이름조차 추출하지 못하면 초기 정보를 제공할 수 없다고 판단한다.
        if (name == null || name.isBlank()) {
            throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
        }

        String imageUrl = readImageUrl(document);

        return new ExtractedClothes(name, imageUrl);
    }

    private String readMeta(Document document, String selector) {
        Element element = document.selectFirst(selector);

        if (element == null) {
            return null;
        }

        String content = element.attr("content").strip();
        return content.isBlank() ? null : content;
    }

    private String readImageUrl(Document document) {
        Element element = document.selectFirst("meta[property=og:image]");

        if (element == null) {
            return null;
        }

        // /images/item.jpg 같은 상대 경로도 절대 URL로 변환한다.
        String absoluteUrl = element.absUrl("content");

        try {
            URI uri = URI.create(absoluteUrl);

            // 이미지 URL은 HTTP/HTTPS 주소만 결과에 포함한다.
            if (uri.getHost() == null
                    || (!"https".equalsIgnoreCase(uri.getScheme())
                    && !"http".equalsIgnoreCase(uri.getScheme()))) {
                return null;
            }

            return uri.toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // 저장 엔티티와 구분되는 추출 결과다.
    public record ExtractedClothes(
            String name,
            String imageUrl
    ) {
    }
}