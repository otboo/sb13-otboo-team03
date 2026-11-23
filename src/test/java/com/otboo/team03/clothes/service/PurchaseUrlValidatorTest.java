package com.otboo.team03.clothes.service;

import com.otboo.team03.global.exception.OtbooException;
import org.junit.jupiter.api.Test;
import java.net.URI;
import static org.junit.jupiter.api.Assertions.*;

class PurchaseUrlValidatorTest {
    private final PurchaseUrlValidator validator = new PurchaseUrlValidator();

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "https://store.musinsa.com/app/goods/123, https://www.musinsa.com/products/123",
            "https://m.store.musinsa.com/app/goods/123/0, https://www.musinsa.com/products/123",
            "HTTPS://WWW.MUSINSA.COM:443/products/123?source=app, https://www.musinsa.com/products/123",
            "https://www.29cm.co.kr/products/123/, https://www.29cm.co.kr/products/123",
            "https://product.29cm.co.kr/catalog/123?source=brand, https://www.29cm.co.kr/products/123",
            "https://zigzag.kr/catalog/products/123, https://zigzag.kr/catalog/products/123",
            "https://www.wconcept.co.kr/Product/123, https://www.wconcept.co.kr/Product/123"
    })
    void resolvesSupportedProductLinks(String url, String expected) {
        assertEquals(URI.create(expected), validator.validate(url));
    }

    @Test
    void rejectsMissingMalformedAndUnsafeUrls() {
        for (String url : new String[]{null, "", "   ", "https://www.musinsa.com/" + "a".repeat(2048),
                "https://www.musinsa.com/a b", "http://www.musinsa.com/products/123",
                "https:/products/123", "https://example.com/product", "https://localhost/product",
                "https://www.musinsa.com.evil.com/product", "https://user:password@www.musinsa.com/product",
                "https://www.musinsa.com:8080/product"}) {
            assertThrows(OtbooException.class, () -> validator.validate(url), String.valueOf(url));
        }
    }

    @Test
    void acceptsUrlAtLengthLimit() {
        String prefix = "https://zigzag.kr/catalog/products/1?q=";
        String url = prefix + "a".repeat(2048 - prefix.length());
        assertEquals(URI.create(url), validator.validate(url));
    }
}
