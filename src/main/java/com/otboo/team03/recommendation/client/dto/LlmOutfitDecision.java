package com.otboo.team03.recommendation.client.dto;

import java.util.List;
import java.util.UUID;

/** 제목·전체 등록 속성·날씨를 해석한 뒤 선택한 소유 의상 ID만 반환한다. */
public record LlmOutfitDecision(List<UUID> clothesIds) { }
