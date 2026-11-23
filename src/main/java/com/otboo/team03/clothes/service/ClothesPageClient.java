package com.otboo.team03.clothes.service;

import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

/** 허용한 쇼핑몰만 요청하며 리다이렉트는 따라가지 않는다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClothesPageClient {
    private final PurchaseUrlValidator validator;
    private static final int MAX_BYTES = 2 * 1024 * 1024;

    public Document fetch(String url) {
        URI uri = validator.validate(url);
        try {
            var response = Jsoup.connect(uri.toString())
                    .userAgent("Otboo/1.0 (product metadata extraction)")
                    .timeout(5000).maxBodySize(MAX_BYTES + 1)
                    .followRedirects(false).ignoreHttpErrors(true).execute();
            if (response.statusCode() != 200
                    || response.contentType() == null
                    || !response.contentType().toLowerCase(Locale.ROOT).split(";", 2)[0].strip().equals("text/html")
                    || response.bodyAsBytes().length > MAX_BYTES) {
                log.warn("Purchase page rejected: host={}, status={}, type={}",
                        uri.getHost(), response.statusCode(), response.contentType());
                if (response.statusCode() == 403 && "www.musinsa.com".equals(uri.getHost())) {
                    throw new OtbooException(ErrorCode.MUSINSA_ACCESS_RESTRICTED);
                }
                throw new OtbooException(ErrorCode.CLOTHES_EXTRACTION_FAILED);
            }
            return Jsoup.parse(response.body(), uri.toString());
        } catch (IOException e) {
            log.warn("Purchase page request failed: host={}, reason={}", uri.getHost(), e.getClass().getSimpleName());
            throw new OtbooException(ErrorCode.CLOTHES_EXTRACTION_FAILED, e);
        }
    }
}
