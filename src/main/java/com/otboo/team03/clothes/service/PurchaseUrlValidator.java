package com.otboo.team03.clothes.service;

import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/**
 * 구매 링크의 형식과 허용 호스트를 검증한다.
 * 실제 페이지 요청이나 상품 상세 페이지 여부 확인은 수행하지 않는다.
 */
@Component
public class PurchaseUrlValidator {
    // 접속을 허용할 호스트를 정확히 지정한다.
    // endsWith 비교 대신 완전 일치를 사용하여 유사 도메인을 거부한다.
    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "musinsa.com", //무신사
            "www.musinsa.com", //무신사
            "store.musinsa.com", //무신사
            "zigzag.kr", //지그재그
            "www.zigzag.kr", //지그재그
            "29cm.co.kr", //29cm
            "www.29cm.co.kr" //29cm
    );
    /**
     * 유효한 구매 링크를 URI로 반환한다.
     * 검증 실패 시 기존 공통 입력 오류로 처리한다.
     */
    public URI validate(String url) {
        // 누락·공백 입력과 정책상 최대 길이를 초과한 URL을 거부한다.
        if (url == null || url.isBlank() || url.length() > 2048) {
            throw invalidUrl();
        }

        try {
            // 앞뒤 공백을 제거하고 URL을 구성 요소별로 분석한다.
            URI uri = new URI(url.strip());
            String host = uri.getHost();

            if (!"https".equalsIgnoreCase(uri.getScheme()) //HTTS만 허용
                    || host == null //호스트가 없는 상대 경로 등은 거부
                    || !ALLOWED_HOSTS.contains(
                    host.toLowerCase(Locale.ROOT)) //허용 호스트와 비교
                    || uri.getRawUserInfo() != null //URL 내부 인증 정보 거부
                    || (uri.getPort() != -1 && uri.getPort() != 443)) {
                //포트 생략 또는 HTTPS 기본 포트인 443 허용한다.
                throw invalidUrl();
            }

            return uri;
        } catch (URISyntaxException e) {
            // URI 문법에 맞지 않는 입력도 동일한 입력 오류로 반환한다.
            throw invalidUrl();
        }
    }

    private OtbooException invalidUrl() {
        return new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
    }
}