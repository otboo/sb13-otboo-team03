package com.otboo.team03.clothes.service;

import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class PurchaseUrlValidator {
    private static final Pattern MUSINSA = Pattern.compile("/(?:products/|app/goods/)([0-9]+)/?");
    private static final Pattern MUSINSA_MOBILE = Pattern.compile("/app/goods/([0-9]+)(?:/0)?/?");
    private static final Pattern CM29 = Pattern.compile("/(?:catalog|products)/([0-9]+)/?");
    private static final Pattern ZIGZAG = Pattern.compile("/catalog/products/[0-9]+/?");
    private static final Pattern WCONCEPT = Pattern.compile("/Product/[0-9]+/?");

    public URI validate(String url) {
        if (url == null || url.isBlank() || url.length() > 2048) {
            throw new OtbooException(ErrorCode.INVALID_PURCHASE_URL);
        }
        URI uri;
        try {
            uri = URI.create(url.strip());
        } catch (IllegalArgumentException ex) {
            throw new OtbooException(ErrorCode.INVALID_PURCHASE_URL);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                || uri.getRawFragment() != null) {
            throw new OtbooException(ErrorCode.INVALID_PURCHASE_URL);
        }
        Pattern path = switch (uri.getHost().toLowerCase(Locale.ROOT)) {
            case "musinsa.com", "www.musinsa.com", "store.musinsa.com" -> MUSINSA;
            case "m.store.musinsa.com" -> MUSINSA_MOBILE;
            case "29cm.co.kr", "www.29cm.co.kr", "product.29cm.co.kr" -> CM29;
            case "zigzag.kr", "www.zigzag.kr" -> ZIGZAG;
            case "wconcept.co.kr", "www.wconcept.co.kr" -> WCONCEPT;
            default -> throw new OtbooException(ErrorCode.UNSUPPORTED_SHOPPING_SITE);
        };
        var match = path.matcher(uri.getRawPath());
        if (!match.matches()) {
            throw new OtbooException(ErrorCode.INVALID_PURCHASE_URL);
        }
        // 추적 파라미터나 구형 주소의 리다이렉트 없이 같은 상품 상세 페이지를 요청한다.
        if (path == MUSINSA || path == MUSINSA_MOBILE) {
            return URI.create("https://www.musinsa.com/products/" + match.group(1));
        }
        if (path == CM29) {
            return URI.create("https://product.29cm.co.kr/catalog/" + match.group(1));
        }
        return uri;
    }
}
