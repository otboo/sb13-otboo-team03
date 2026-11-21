package com.otboo.team03.recommendation.validation;

import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.recommendation.client.dto.LlmOutfitDecision;
import com.otboo.team03.recommendation.client.dto.LlmRecommendationInput;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.otboo.team03.recommendation.support.LlmTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class LlmOutfitValidatorTest {
    private final LlmOutfitValidator validator = new LlmOutfitValidator();

    @Test
    void 사전에_없는_특징도_해당_의상의_근거가_있으면_허용한다() {
        assertThat(validator.isValid(decision(), input())).isTrue();
    }

    @Test
    void 상의하의가_있어도_원피스를_선택할_수_있다() {
        assertThat(validator.isValid(new LlmOutfitDecision(List.of(DRESS_ID),
                List.of(new LlmOutfitDecision.Analysis(DRESS_ID, List.of())), "원피스 선택"), input())).isTrue();
    }

    @Test
    void 다른_사용자_의상이나_중복_ID는_거부한다() {
        assertThat(validIds(List.of(TOP_ID, UUID.randomUUID()))).isFalse();
        assertThat(validIds(List.of(TOP_ID, BOTTOM_ID, TOP_ID))).isFalse();
    }

    @Test
    void 기본_조합_누락이나_원피스와_상의하의_혼합은_거부한다() {
        assertThat(validIds(List.of(TOP_ID))).isFalse();
        assertThat(validIds(List.of(TOP_ID, BOTTOM_ID, DRESS_ID))).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = ClothesType.class, names = {"UNDERWEAR", "ETC", "TOP", "SOCKS"})
    void 제외_종류_같은종류_중복_신발없는양말은_거부한다(ClothesType type) {
        UUID extra = UUID.randomUUID();
        List<LlmRecommendationInput.Item> wardrobe = new ArrayList<>(input().wardrobe());
        wardrobe.add(new LlmRecommendationInput.Item(extra, "추가 의상", type, List.of()));
        var expanded = new LlmRecommendationInput(input().weather(), 3, input().previousOutfitIds(), wardrobe);
        var decision = new LlmOutfitDecision(List.of(TOP_ID, BOTTOM_ID, extra), List.of(
                new LlmOutfitDecision.Analysis(TOP_ID, List.of()),
                new LlmOutfitDecision.Analysis(BOTTOM_ID, List.of()),
                new LlmOutfitDecision.Analysis(extra, List.of())), "코디");
        assertThat(validator.isValid(decision, expanded)).isFalse();
    }

    @Test
    void 의상_제목과_속성에_없는_근거는_거부한다() {
        assertThat(validFeature(new LlmOutfitDecision.Feature("방수", "가능", "완전 방수", LlmOutfitDecision.Source.TITLE)))
                .isFalse();
        assertThat(validFeature(new LlmOutfitDecision.Feature("색상", "검정", "검정", LlmOutfitDecision.Source.ATTRIBUTE)))
                .isFalse(); // 검정은 다른 의상의 속성이므로 근거로 사용할 수 없다.
        assertThat(validFeature(new LlmOutfitDecision.Feature("비침", "있음", "비침 있음", LlmOutfitDecision.Source.TITLE)))
                .isFalse(); // 문구가 실제 있는 출처도 맞아야 한다.
    }

    @Test
    void 분석_누락이나_다른_의상_분석은_거부한다() {
        assertThat(validator.isValid(new LlmOutfitDecision(List.of(TOP_ID, BOTTOM_ID), List.of(), "선택"), input())).isFalse();
        assertThat(validator.isValid(new LlmOutfitDecision(List.of(TOP_ID, BOTTOM_ID), List.of(
                new LlmOutfitDecision.Analysis(TOP_ID, List.of()),
                new LlmOutfitDecision.Analysis(DRESS_ID, List.of())), "선택"), input())).isFalse();
    }

    @Test
    void null과_빈_응답은_거부한다() {
        assertThat(validator.isValid(null, input())).isFalse();
        assertThat(validator.isValid(new LlmOutfitDecision(null, null, null), input())).isFalse();
        assertThat(validator.isValid(new LlmOutfitDecision(List.of(), List.of(), ""), input())).isFalse();
        assertThat(validFeature(new LlmOutfitDecision.Feature("", "있음", "크롭", LlmOutfitDecision.Source.TITLE))).isFalse();
    }

    private boolean validIds(List<UUID> ids) {
        return validator.isValid(new LlmOutfitDecision(ids, ids.stream()
                .map(id -> new LlmOutfitDecision.Analysis(id, List.of())).toList(), "선택"), input());
    }

    private boolean validFeature(LlmOutfitDecision.Feature feature) {
        return validator.isValid(new LlmOutfitDecision(List.of(TOP_ID, BOTTOM_ID), List.of(
                new LlmOutfitDecision.Analysis(TOP_ID, List.of(feature)),
                new LlmOutfitDecision.Analysis(BOTTOM_ID, List.of())), "선택"), input());
    }
}
