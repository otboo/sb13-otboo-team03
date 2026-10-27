package com.otboo.team03.clothes.dto.request;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ClothesType {

    TOP("상의"),
    BOTTOM("하의"),
    DRESS("원피스"),
    OUTER("아우터"),
    UNDERWEAR("속옷"),
    ACCESSORY("액세서리"),
    SHOES("신발"),
    SOCKS("양말"),
    HAT("모자"),
    BAG("가방"),
    SCARF("스카프"),
    ETC("기타");

    private final String description;
}