package com.otboo.team03.clothes.service;

import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClothesExtractionServiceTest {
    private final ClothesPageClient client = mock(ClothesPageClient.class);
    private final ClothesExtractionService service = new ClothesExtractionService(client, new ClothesHtmlParser());

    // 각 도메인의 상대 이미지 URL 해석을 검증하는 합성 HTML이며 실사이트 응답이 아니다.
    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"www.musinsa.com,/products/123", "product.29cm.co.kr,/catalog/123", "www.wconcept.co.kr,/Product/123"})
    void returnsDraftWithoutInventingTypeOrAttributes(String host, String path) {
        String url = "https://" + host + path;
        when(client.fetch(url)).thenReturn("<meta property='og:type' content='product'><meta property='og:title' content='면 셔츠'><meta property='og:image' content='/shirt.jpg'>");
        UUID owner = UUID.randomUUID();
        var result = service.extract(url, owner);
        assertThat(result.ownerId()).isEqualTo(owner);
        assertThat(result.id()).isNull();
        assertThat(result.name()).isEqualTo("면 셔츠");
        assertThat(result.imageUrl()).isEqualTo("https://" + host + "/shirt.jpg");
        assertThat(result.type()).isNull();
        assertThat(result.attributes()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "<title>로그인</title>", "<meta property='og:title' content=' '>", "<h1>Access denied</h1>", "<meta property='og:title' content='쇼핑몰 로그인'><meta property='og:type' content='website'>"})
    void rejectsMissingMetadata(String html) {
        when(client.fetch(anyString())).thenReturn(html);
        assertThatThrownBy(() -> service.extract("https://www.musinsa.com/products/1", UUID.randomUUID()))
                .isInstanceOfSatisfying(OtbooException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CLOTHES_EXTRACTION_FAILED));
    }

    @Test
    void missingImageStillAllowsNamePrefill() {
        when(client.fetch(anyString())).thenReturn("<meta property='og:type' content='product'><meta property='og:title' content='셔츠'>");
        assertThat(service.extract("https://www.musinsa.com/products/1", UUID.randomUUID()).imageUrl()).isNull();
    }
    @ParameterizedTest
    @ValueSource(strings = {
            "{\"@type\":\"Product\"}",
            "[{\"@type\":\"Product\"}]",
            "{\"@graph\":[{\"@type\":[\"Thing\",\"Product\"]}]}",
            "{\"@type\":\"WebPage\",\"mainEntity\":{\"@type\":\"https://schema.org/Product\"}}"
    })
    void acceptsProductStructuredData(String json) {
        when(client.fetch(anyString())).thenReturn("<meta property='og:title' content='셔츠'>"
                + "<script type='application/ld+json'>" + json + "</script>");
        assertThat(service.extract("https://product.29cm.co.kr/catalog/123", UUID.randomUUID()).name())
                .isEqualTo("셔츠");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{broken", "null", "{\"@type\":\"WebSite\"}",
            "{\"@type\":\"ItemList\",\"itemListElement\":[{\"@type\":\"Product\"}]}"})
    void rejectsGenericPagesAndRecommendationLists(String json) {
        when(client.fetch(anyString())).thenReturn("<meta property='og:title' content='메인 페이지'>"
                + "<script type='application/ld+json'>" + json + "</script>");
        assertThatThrownBy(() -> service.extract("https://www.musinsa.com/products/1", UUID.randomUUID()))
                .isInstanceOfSatisfying(OtbooException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CLOTHES_EXTRACTION_FAILED));
    }

    @ParameterizedTest
    @ValueSource(strings = {"product", " og:product ", "PRODUCT"})
    void acceptsProductOpenGraphTypes(String type) {
        when(client.fetch(anyString())).thenReturn("<meta property='og:title' content='셔츠'>"
                + "<meta property='og:type' content='" + type + "'>");
        assertThat(service.extract("https://www.musinsa.com/products/1", UUID.randomUUID()).name())
                .isEqualTo("셔츠");
    }

    @ParameterizedTest
    @ValueSource(ints = {255, 256})
    void enforcesNameLengthLimit(int length) {
        String name = "가".repeat(length);
        when(client.fetch(anyString())).thenReturn("<meta property='og:type' content='product'>"
                + "<meta property='og:title' content='" + name + "'>");
        if (length == 255) {
            assertThat(service.extract("https://www.musinsa.com/products/1", UUID.randomUUID()).name())
                    .isEqualTo(name);
        } else {
            assertThatThrownBy(() -> service.extract("https://www.musinsa.com/products/1", UUID.randomUUID()))
                    .isInstanceOfSatisfying(OtbooException.class, e ->
                            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CLOTHES_EXTRACTION_FAILED));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "[null, 1, {}]", "{\"@type\":[1, \"Thing\"]}",
            "{\"@type\":42}", "{}"})
    void rejectsStructuredDataWithoutProduct(String json) {
        when(client.fetch(anyString())).thenReturn("<meta property='og:title' content='메인 페이지'>"
                + "<script type='application/ld+json'>" + json + "</script>");
        assertThatThrownBy(() -> service.extract("https://www.musinsa.com/products/1", UUID.randomUUID()))
                .isInstanceOfSatisfying(OtbooException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CLOTHES_EXTRACTION_FAILED));
    }

    @Test
    void continuesAfterInvalidJsonBlockToFindProduct() {
        when(client.fetch(anyString())).thenReturn("<meta property='og:title' content='셔츠'>"
                + "<script type='application/ld+json'>{broken</script>"
                + "<script type='application/ld+json'>[{}, {\"@type\":\"http://schema.org/Product\"}]</script>");
        assertThat(service.extract("https://www.musinsa.com/products/1", UUID.randomUUID()).name())
                .isEqualTo("셔츠");
    }

}
