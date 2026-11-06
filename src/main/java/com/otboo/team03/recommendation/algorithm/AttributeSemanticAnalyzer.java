package com.otboo.team03.recommendation.algorithm;

import java.util.Map;
import java.util.Set;

/** 두께 관련 표현을 해석하며, 사전에 없는 표현은 추측하지 않는다. */
public class AttributeSemanticAnalyzer {

    private static final Set<String> THICKNESS_NAMES = Set.of("두께", "두께감");
    private static final Map<String, ThicknessLevel> THICKNESS_VALUES = Map.of(
            "얇음", ThicknessLevel.LIGHT,
            "보통", ThicknessLevel.MEDIUM,
            "두꺼움", ThicknessLevel.HEAVY,
            "도톰함", ThicknessLevel.HEAVY
    );

    public ThicknessLevel analyze(String attributeName, String value) {
        if (attributeName == null || value == null) {
            return ThicknessLevel.UNKNOWN;
        }

        if (!THICKNESS_NAMES.contains(attributeName.strip())) {
            return ThicknessLevel.UNKNOWN;
        }

        return THICKNESS_VALUES.getOrDefault(value.strip(), ThicknessLevel.UNKNOWN);
    }
}
