package com.otboo.team03.recommendation.algorithm;

import com.otboo.team03.recommendation.entity.SleeveLength;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/** 소매 관련 표현을 해석하며, 사전에 없는 표현은 추측하지 않는다. */
@Component
public class SleeveLengthAnalyzer {

    private static final Set<String> SLEEVE_NAMES = Set.of("소매", "소매 길이");
    private static final Map<String, SleeveLength> SLEEVE_VALUES = Map.of(
            "반팔", SleeveLength.SHORT,
            "긴팔", SleeveLength.LONG
    );

    public SleeveLength analyze(String attributeName, String value) {
        if (attributeName == null || value == null) {
            return SleeveLength.UNKNOWN;
        }
        if (!SLEEVE_NAMES.contains(attributeName.strip())) {
            return SleeveLength.UNKNOWN;
        }
        return SLEEVE_VALUES.getOrDefault(value.strip(), SleeveLength.UNKNOWN);
    }
}
