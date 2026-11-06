package com.otboo.team03.clothes.service;

import com.otboo.team03.global.exception.OtbooException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClothesHtmlParserTest {

    private final ClothesHtmlParser parser = new ClothesHtmlParser();
    private static final String PAGE_URL = "https://www.musinsa.com/products/123";

    @Test
    void HTML_제목보다_OpenGraph_상품명을_우선한다() {
        var result = parser.parse("""
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
        var result = parser.parse("<title>린넨 셔츠</title>", PAGE_URL);

        assertEquals("린넨 셔츠", result.name());
        assertNull(result.imageUrl());
    }

    @Test
    void 빈_OpenGraph_제목이면_HTML_제목을_사용한다() {
        var result = parser.parse("""
                <title>청바지</title>
                <meta property="og:title" content="   ">
                """, PAGE_URL);

        assertEquals("청바지", result.name());
    }

    @Test
    void 상대_이미지_경로를_절대_URL로_변환한다() {
        var result = parser.parse("""
                <meta property="og:title" content="청바지">
                <meta property="og:image" content="/images/jeans.jpg">
                """, PAGE_URL);

        assertEquals("https://www.musinsa.com/images/jeans.jpg", result.imageUrl());
    }

    @Test
    void 제목이_없거나_공백이면_추출에_실패한다() {
        for (String html : new String[]{"<html><head></head></html>", "<title>   </title>"}) {
            assertThrows(OtbooException.class, () -> parser.parse(html, PAGE_URL));
        }
    }

    @Test
    void HTTP_계열이_아닌_이미지는_반환하지_않는다() {
        for (String image : new String[]{"javascript:alert(1)", "data:image/png;base64,AAAA", "file:///tmp/image.png"}) {
            var result = parser.parse("""
                    <title>셔츠</title>
                    <meta property="og:image" content="%s">
                    """.formatted(image), PAGE_URL);

            assertEquals("셔츠", result.name());
            assertNull(result.imageUrl(), image);
        }
    }

    @Test
    void HTTP_이미지도_허용한다() {
        var result = parser.parse("""
                <title>셔츠</title>
                <meta property="og:image" content="http://cdn.example.com/shirt.jpg">
                """, PAGE_URL);

        assertEquals("http://cdn.example.com/shirt.jpg", result.imageUrl());
    }
}
