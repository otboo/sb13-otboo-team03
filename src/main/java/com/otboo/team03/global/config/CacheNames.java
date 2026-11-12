package com.otboo.team03.global.config;

public final class CacheNames {

    // Redis 캐시 키를 기존 Redis 데이터와 구분하기 위한 공통 접두사
    public static final String KEY_PREFIX = "otboo:cache:";

    // 각 도메인 담당자는 캐시 적용 시 이곳에 캐시 이름 상수를 추가하여 사용


    private CacheNames() {
    }
}
