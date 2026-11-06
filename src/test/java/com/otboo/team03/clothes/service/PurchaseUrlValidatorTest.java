package com.otboo.team03.clothes.service;

import com.otboo.team03.global.exception.OtbooException;
import org.junit.jupiter.api.Test;
import java.net.URI;
import static org.junit.jupiter.api.Assertions.*;

class PurchaseUrlValidatorTest {
    private final PurchaseUrlValidator validator = new PurchaseUrlValidator();

    @Test
    void acceptsSupportedHostsAndDefaultHttpsPort() {
        for (String host : new String[]{"musinsa.com", "www.musinsa.com", "store.musinsa.com",
                "zigzag.kr", "www.zigzag.kr", "29cm.co.kr", "www.29cm.co.kr"}) {
            String url = "https://" + host + "/products/123?color=blue";
            assertEquals(URI.create(url), validator.validate(url));
        }
        assertEquals(URI.create("HTTPS://WWW.MUSINSA.COM:443/products/123"),
                validator.validate("  HTTPS://WWW.MUSINSA.COM:443/products/123  "));
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
        String prefix = "https://www.musinsa.com/";
        String url = prefix + "a".repeat(2048 - prefix.length());
        assertEquals(URI.create(url), validator.validate(url));
    }
}
