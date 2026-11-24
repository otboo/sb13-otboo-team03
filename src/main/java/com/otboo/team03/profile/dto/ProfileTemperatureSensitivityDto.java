package com.otboo.team03.profile.dto;

/** null인 민감도와 프로필 행 자체가 없는 경우를 구분하는 단일 필드 조회 결과. */
public record ProfileTemperatureSensitivityDto(Integer temperatureSensitivity) {
}
