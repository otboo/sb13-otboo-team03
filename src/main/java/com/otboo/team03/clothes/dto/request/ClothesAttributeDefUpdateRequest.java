package com.otboo.team03.clothes.dto.request;

import java.util.List;

public record ClothesAttributeDefUpdateRequest(
   String name, //속성 정의 이름
   List<String> selectableValues //선택 가능한 값 목록
) {}
