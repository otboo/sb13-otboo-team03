package com.otboo.team03.clothes.service;

import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

/** 허용한 쇼핑몰만 요청하며 리다이렉트는 따라가지 않는다. */
@Component
public class ClothesPageClient {
    private static final Set<String> HOSTS = Set.of(
            "musinsa.com", "www.musinsa.com", "store.musinsa.com",
            "29cm.co.kr", "www.29cm.co.kr", "product.29cm.co.kr",
            "wconcept.co.kr", "www.wconcept.co.kr");
    private static final int MAX_BYTES = 2 * 1024 * 1024;

    public String fetch(String url) {
        URI uri = validate(url);
        try {
            var response = Jsoup.connect(uri.toString())
                    .userAgent("Otboo/1.0 (product metadata extraction)")
                    .timeout(5000).maxBodySize(MAX_BYTES + 1)
                    .followRedirects(false).ignoreHttpErrors(true).execute();
            if (response.statusCode() != 200
                    || response.contentType() == null
                    || !response.contentType().toLowerCase(Locale.ROOT).startsWith("text/html")
                    || response.bodyAsBytes().length > MAX_BYTES) {
                throw new OtbooException(ErrorCode.CLOTHES_EXTRACTION_FAILED);
            }
            return response.body();
        } catch (IOException e) {
            throw new OtbooException(ErrorCode.CLOTHES_EXTRACTION_FAILED, e);
        }
    }

    URI validate(String url) {
        if (url == null || url.isBlank() || url.length() > 2048) {
            throw new OtbooException(ErrorCode.INVALID_PURCHASE_URL);
        }
        final URI uri;
        try {
            uri = URI.create(url.strip());
        } catch (IllegalArgumentException e) {
            throw new OtbooException(ErrorCode.INVALID_PURCHASE_URL);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                || uri.getRawFragment() != null) {
            throw new OtbooException(ErrorCode.INVALID_PURCHASE_URL);
        }
        // suffix 비교를 사용하지 않아 유사 도메인 및 임의 서브도메인을 허용하지 않는다.
        if (!HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))) {
            throw new OtbooException(ErrorCode.UNSUPPORTED_SHOPPING_SITE);
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getRawPath();
        boolean productPath = host.endsWith("musinsa.com")
                ? path.matches("/(?:products|app/goods)/[0-9]+/?")
                : host.endsWith("29cm.co.kr")
                ? path.matches("/(?:catalog|products)/[0-9]+/?")
                : path.matches("/Product/[0-9]+/?");
        if (!productPath) {
            throw new OtbooException(ErrorCode.INVALID_PURCHASE_URL);
        }
        return uri;
    }
}
