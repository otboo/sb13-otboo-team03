package com.otboo.team03.clothes.service;

import com.otboo.team03.global.exception.OtbooException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClothesHtmlParserTest {

    private final ClothesHtmlParser parser = new ClothesHtmlParser();
    private static final String PAGE_URL = "https://www.musinsa.com/products/123";

    private ClothesHtmlParser.ExtractedClothes parseProduct(String html, String url) {
        return parser.parse(org.jsoup.Jsoup.parse("<meta property='og:type' content='product'>" + html, url));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"", "   ", "\t\n", "\u2003"})
    void 빈_이미지_값을_상품_페이지_URL로_반환하지_않는다(String content) {
        var result = parseProduct("""
                <title>셔츠</title>
                <meta property="og:image" content="%s">
                """.formatted(content), PAGE_URL);

        assertEquals("셔츠", result.name());
        assertNull(result.imageUrl());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "'{\"@type\":\"WebPage\",\"mainEntity\":{\"@id\":\"#current\"}}', '#current', /current.jpg",
            "'{}', '/products/123', /current.jpg",
            "'{}', '#current',"
    })
    void 여러_상품이면_현재_상품의_이미지만_선택한다(String pageNode, String productId, String expected) {
        var html = """
                <meta property="og:title" content="현재 상품">
                <script type="application/ld+json">{"@graph":[
                  {"@type":"Product","@id":"#other","image":"/other.jpg"},%s]}
                </script><script type="application/ld+json">
                  {"@type":"Product","@id":"%s","image":"/current.jpg"}
                </script>
                """.formatted(pageNode, productId);
        var result = parser.parse(org.jsoup.Jsoup.parse(html, PAGE_URL));
        assertEquals("현재 상품", result.name());
        assertEquals(expected == null ? null : "https://www.musinsa.com" + expected, result.imageUrl());
    }

    @Test
    void 다른_상품_URL은_mainEntity여도_현재_상품으로_선택하지_않는다() {
        String html = """
                <script type="application/ld+json">{"@type":"WebPage","mainEntity":{
                "@type":"Product","url":"/products/999","name":"다른 옷","image":"/wrong.jpg"}}</script>
                """;
        assertThrows(OtbooException.class, () -> parser.parse(org.jsoup.Jsoup.parse(html, PAGE_URL)));
        var result = parseProduct("<title>현재 옷</title>" + html, PAGE_URL);
        assertEquals("현재 옷", result.name());
        assertNull(result.imageUrl());
    }

    @Test
    void 상품_메타데이터에_사진만_빠져도_무신사_대표_이미지를_찾는다() {
        String html = """
                <title>브랜드 재킷 | 무신사</title><meta property="og:title" content="재킷">
                <img src="https://image.msscdn.net/images/goods_img/date/123/p.jpg" alt="브랜드 재킷">
                """;
        var result = parseProduct(html, PAGE_URL);
        assertEquals("재킷", result.name());
        assertEquals("https://image.msscdn.net/images/goods_img/date/123/p.jpg", result.imageUrl());
    }

    @Test
    void 무신사_상품번호와_제목이_일치하는_이미지를_사용한다() {
        String html = """
                <title>브랜드 플리스 재킷 - 사이즈 &amp; 후기 | 무신사</title>
                <img src="https://image.msscdn.net/images/goods_img/20260812/7054623/photo.jpg" alt="브랜드 플리스 재킷">
                """;
        var result = parser.parse(org.jsoup.Jsoup.parse(html, "https://www.musinsa.com/products/7054623"));
        assertEquals("브랜드 플리스 재킷", result.name());
        assertTrue(result.imageUrl().endsWith("/7054623/photo.jpg"));
        assertThrows(OtbooException.class, () -> parser.parse(org.jsoup.Jsoup.parse(html, PAGE_URL)));
    }

    @Test
    void 이십구센티_상품번호와_제목이_일치하면_예약배송_표시가_있어도_읽는다() {
        String html = """
                <title>리버시블 점퍼 - 모든 취향 곁에, 29CM</title><h2>[예약배송] 리버시블 점퍼</h2>
                <h3>상품번호 :</h3><span>4296738</span>
                """;
        var result = parser.parse(org.jsoup.Jsoup.parse(html, "https://www.29cm.co.kr/products/4296738"));
        assertEquals("리버시블 점퍼", result.name());
        assertThrows(OtbooException.class, () -> parser.parse(org.jsoup.Jsoup.parse(html,
                "https://www.29cm.co.kr/products/429")));
    }

    @Test
    void 이미지_content_속성이_없으면_반환하지_않는다() {
        var result = parseProduct("<title>셔츠</title><meta property='og:image'>", PAGE_URL);
        assertNull(result.imageUrl());
    }

    @Test
    void 잘못된_URI_이미지는_반환하지_않는다() {
        var result = parseProduct("""
                <title>셔츠</title>
                <meta property="og:image" content="https://cdn.example.com/[photo].jpg">
                """, PAGE_URL);
        assertNull(result.imageUrl());
    }

    @Test
    void HTML_제목보다_OpenGraph_상품명을_우선한다() {
        var result = parseProduct("""
                <html><head>
                  <title>쇼핑몰 기본 제목</title>
                  <meta property="og:title" content="  코튼 티셔츠  ">
                  <meta property="og:image" content="https://cdn.example.com/shirt.jpg">
                </head></html>
                """, PAGE_URL);

        assertEquals("코튼 티셔츠", result.name());
        assertEquals("https://cdn.example.com/shirt.jpg", result.imageUrl());
    }

    @Test
    void 메타데이터가_없으면_HTML_제목을_사용하고_이미지는_없다() {
        var result = parseProduct("<title>린넨 셔츠</title>", PAGE_URL);

        assertEquals("린넨 셔츠", result.name());
        assertNull(result.imageUrl());
    }

    @Test
    void 빈_OpenGraph_제목이면_HTML_제목을_사용한다() {
        var result = parseProduct("""
                <title>청바지</title>
                <meta property="og:title" content="   ">
                """, PAGE_URL);

        assertEquals("청바지", result.name());
    }

    @Test
    void 상대_이미지_경로를_절대_URL로_변환한다() {
        var result = parseProduct("""
                <meta property="og:title" content="청바지">
                <meta property="og:image" content="/images/jeans.jpg">
                """, PAGE_URL);

        assertEquals("https://www.musinsa.com/images/jeans.jpg", result.imageUrl());
    }

    @Test
    void 제목이_없거나_공백이면_추출에_실패한다() {
        for (String html : new String[]{"<html><head></head></html>", "<title>   </title>"}) {
            assertThrows(OtbooException.class, () -> parseProduct(html, PAGE_URL));
        }
    }

    @Test
    void HTTP_계열이_아닌_이미지는_반환하지_않는다() {
        for (String image : new String[]{"javascript:alert(1)", "data:image/png;base64,AAAA", "file:///tmp/image.png"}) {
            var result = parseProduct("""
                    <title>셔츠</title>
                    <meta property="og:image" content="%s">
                    """.formatted(image), PAGE_URL);

            assertEquals("셔츠", result.name());
            assertNull(result.imageUrl(), image);
        }
    }

    @Test
    void HTTP_이미지도_허용한다() {
        var result = parseProduct("""
                <title>셔츠</title>
                <meta property="og:image" content="http://cdn.example.com/shirt.jpg">
                """, PAGE_URL);

        assertEquals("http://cdn.example.com/shirt.jpg", result.imageUrl());
    }
}
