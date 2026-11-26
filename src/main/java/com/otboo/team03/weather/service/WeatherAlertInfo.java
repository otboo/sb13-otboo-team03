package com.otboo.team03.weather.service;

// 날씨 급변(강수, 기온 급변 등) 감지 시 생성되는 알림 정보 DTO
public record WeatherAlertInfo(
    String title,
    String content
) {
}