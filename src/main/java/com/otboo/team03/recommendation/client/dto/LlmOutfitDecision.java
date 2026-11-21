package com.otboo.team03.recommendation.client.dto;

import java.util.List;
import java.util.UUID;

/** 특징의 name/value는 자유 문자열이며 두께·소매 등 고정 사전으로 제한하지 않는다. */
public record LlmOutfitDecision(List<UUID> clothesIds, List<Analysis> analyses, String reason) {
    public record Analysis(UUID clothesId, List<Feature> features) { }

    public record Feature(String name, String value, String evidence, Source source) { }

    public enum Source {
        TITLE, ATTRIBUTE
    }
}
