package com.otboo.team03.clothes.service;

import com.otboo.team03.global.exception.OtbooException;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

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

    static Stream<Arguments> invalidMusinsaImages() {
        String image = "https://image.msscdn.net/goods_img/123/main.jpg";
        return Stream.of(
                Arguments.of("", "재킷"),
                Arguments.of("https://other.example/goods_img/123/main.jpg", "재킷"),
                Arguments.of("https://image.msscdn.net/banner/123/main.jpg", "재킷"),
                Arguments.of(image, ""),
                Arguments.of(image, "가".repeat(256)),
                Arguments.of(image, "다른 상품"));
    }

    @ParameterizedTest
    @MethodSource("invalidMusinsaImages")
    void 무신사_대표사진_조건을_충족하지_않으면_사진을_생략한다(String src, String alt) {
        String title = "재킷 " + "가".repeat(256);
        var result = parseProduct("""
                <title>%s</title><meta property="og:title" content="재킷">
                <img src="%s" alt="%s">
                """.formatted(title, src, alt), PAGE_URL);
        assertEquals("재킷", result.name());
        assertNull(result.imageUrl());
    }

    static Stream<Arguments> invalid29cmTitles() {
        return Stream.of(Arguments.of("", "점퍼"), Arguments.of("가".repeat(256), "가".repeat(256)),
                Arguments.of("점퍼", "다른 상품"));
    }

    @ParameterizedTest
    @MethodSource("invalid29cmTitles")
    void 이십구센티_상품번호만_일치하면_추출을_거절한다(String title, String heading) {
        var document = Jsoup.parse("<title>" + title + "</title><h2>" + heading + "</h2>상품번호: 123",
                "https://www.29cm.co.kr/products/123");
        assertThrows(OtbooException.class, () -> parser.parse(document));
    }

    @Test
    void 여러_mainEntity_후보에서_페이지_URL이_일치하는_상품을_고른다() {
        var result = parseProduct("""
                <script type="application/ld+json">
                {"mainEntity":["#first","#second"],"@graph":[
                  {"@type":"Product","@id":"#first","name":"다른 상품"},
                  {"@type":"Product","@id":"#second","url":"/products/123","name":"현재 상품"}]}
                </script>
                """, PAGE_URL);
        assertEquals("현재 상품", result.name());
    }

    @Test
    void mainEntity가_누락된_상품을_가리키면_다른_상품의_사진을_쓰지_않는다() {
        var result = parseProduct("""
                <title>현재 상품</title><script type="application/ld+json">
                {"mainEntity":"#missing","@graph":[{"@type":"Product","name":"추천","image":"/other.jpg"}]}
                </script>
                """, PAGE_URL);
        assertEquals("현재 상품", result.name());
        assertNull(result.imageUrl());
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://other.example/products/123", "http://www.musinsa.com/products/123",
            "https://www.musinsa.com:8443/products/123"})
    void 출처가_다른_JSON_LD_상품은_경로가_같아도_제외한다(String url) {
        var document = Jsoup.parse("""
                <script type="application/ld+json">
                {"@type":"Product","url":{"@id":"%s"},"name":"다른 상품"}
                </script>
                """.formatted(url), PAGE_URL);
        assertThrows(OtbooException.class, () -> parser.parse(document));
    }

    @Test
    void 이미지_후보가_모두_유효하지_않으면_상품명만_반환한다() {
        var result = parseProduct("""
                <script type="application/ld+json">
                {"@type":"Product","name":"점퍼","image":["","ftp://cdn.example/main.jpg"]}
                </script>
                """, PAGE_URL);
        assertEquals("점퍼", result.name());
        assertNull(result.imageUrl());
    }
}
