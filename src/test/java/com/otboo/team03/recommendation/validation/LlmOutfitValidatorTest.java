package com.otboo.team03.recommendation.validation;

import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.recommendation.client.dto.LlmOutfitDecision;
import com.otboo.team03.recommendation.client.dto.LlmRecommendationInput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

    @ParameterizedTest
    @MethodSource("invalidStructures")
    void 응답_구조와_추천_이유가_잘못되면_거부한다(LlmOutfitDecision decision) {
        assertThat(validator.isValid(decision, input())).isFalse();
    }

    static Stream<LlmOutfitDecision> invalidStructures() {
        return Stream.concat(Stream.of(
                new LlmOutfitDecision(Collections.nCopies(10, TOP_ID), decision().analyses(), "선택"),
                new LlmOutfitDecision(decision().clothesIds(), null, "선택")),
                Stream.of(null, "", "   ", "x".repeat(1001)).map(reason ->
                        new LlmOutfitDecision(decision().clothesIds(), decision().analyses(), reason)));
    }

    @Test
    void 의상_종류가_없으면_거부한다() {
        var wardrobe = new ArrayList<>(input().wardrobe());
        wardrobe.set(0, new LlmRecommendationInput.Item(TOP_ID, "셔츠", null, List.of()));

        assertThat(validator.isValid(decision(), withWardrobe(wardrobe))).isFalse();
    }

    @ParameterizedTest
    @MethodSource("outfitCombinations")
    void 기본_조합과_양말의_신발_동반_여부를_검증한다(List<ClothesType> types, boolean expected) {
        var wardrobe = types.stream().map(type -> new LlmRecommendationInput.Item(
                UUID.randomUUID(), "의상", type, List.of())).toList();
        var ids = wardrobe.stream().map(LlmRecommendationInput.Item::clothesId).toList();
        var analyses = ids.stream().map(id -> new LlmOutfitDecision.Analysis(id, List.of())).toList();

        assertThat(validator.isValid(new LlmOutfitDecision(ids, analyses, "선택"), withWardrobe(wardrobe)))
                .isEqualTo(expected);
    }

    static Stream<Arguments> outfitCombinations() {
        return Stream.of(
                Arguments.of(List.of(ClothesType.BOTTOM), false),
                Arguments.of(List.of(ClothesType.OUTER), false),
                Arguments.of(List.of(ClothesType.DRESS, ClothesType.TOP), false),
                Arguments.of(List.of(ClothesType.DRESS, ClothesType.BOTTOM), false),
                Arguments.of(List.of(ClothesType.DRESS, ClothesType.SOCKS), false),
                Arguments.of(List.of(ClothesType.DRESS, ClothesType.SOCKS, ClothesType.SHOES), true),
                Arguments.of(List.of(ClothesType.TOP, ClothesType.BOTTOM, ClothesType.SOCKS, ClothesType.SHOES), true));
    }

    @ParameterizedTest
    @MethodSource("invalidAnalyses")
    void 분석의_null과_중복_특징_누락과_개수_초과를_거부한다(List<LlmOutfitDecision.Analysis> analyses) {
        assertThat(validator.isValid(new LlmOutfitDecision(decision().clothesIds(), analyses, "선택"), input()))
                .isFalse();
    }

    static Stream<List<LlmOutfitDecision.Analysis>> invalidAnalyses() {
        var top = new LlmOutfitDecision.Analysis(TOP_ID, List.of());
        var bottom = new LlmOutfitDecision.Analysis(BOTTOM_ID, List.of());
        return Stream.of(
                Arrays.asList(null, bottom),
                List.of(top, top),
                List.of(new LlmOutfitDecision.Analysis(null, List.of()), bottom),
                List.of(new LlmOutfitDecision.Analysis(TOP_ID, null), bottom),
                List.of(new LlmOutfitDecision.Analysis(TOP_ID,
                        Collections.nCopies(9, decision().analyses().get(0).features().get(0))), bottom));
    }

    @ParameterizedTest
    @MethodSource("invalidFeatures")
    void 특징의_필수_텍스트와_길이_출처를_검증한다(LlmOutfitDecision.Feature feature) {
        assertThat(validFeature(feature)).isFalse();
    }

    static Stream<LlmOutfitDecision.Feature> invalidFeatures() {
        var source = LlmOutfitDecision.Source.TITLE;
        return Stream.concat(Stream.of(
                null, new LlmOutfitDecision.Feature("기장", "짧음", "크롭", null)),
                Stream.of(null, "", "   ", "x".repeat(256)).flatMap(text -> Stream.of(
                        new LlmOutfitDecision.Feature(text, "짧음", "크롭", source),
                        new LlmOutfitDecision.Feature("기장", text, "크롭", source)))
        );
    }

    @ParameterizedTest
    @MethodSource("invalidEvidence")
    void 인용_근거는_필수이며_길이_제한을_지켜야_한다(String evidence) {
        assertThat(validFeature(new LlmOutfitDecision.Feature(
                "기장", "짧음", evidence, LlmOutfitDecision.Source.TITLE))).isFalse();
    }

    static Stream<String> invalidEvidence() {
        return Stream.of(null, "", "   ", "x".repeat(513));
    }

    @Test
    void 제목이나_속성값이_null이면_해당_출처로_인용할_수_없다() {
        var wardrobe = new ArrayList<>(input().wardrobe());
        wardrobe.set(0, new LlmRecommendationInput.Item(TOP_ID, null, ClothesType.TOP,
                List.of(new LlmRecommendationInput.Attribute("원단", null))));
        var changedInput = withWardrobe(wardrobe);
        for (var source : LlmOutfitDecision.Source.values()) {
            var feature = new LlmOutfitDecision.Feature("기장", "짧음", "크롭", source);
            assertThat(validator.isValid(withFeatures(Collections.singletonList(feature)), changedInput)).isFalse();
        }
    }

    @Test
    void 텍스트와_특징_개수의_상한값은_허용한다() {
        String evidence = "x".repeat(512);
        var feature = new LlmOutfitDecision.Feature("n".repeat(255), "v".repeat(255),
                evidence, LlmOutfitDecision.Source.TITLE);
        var wardrobe = new ArrayList<>(input().wardrobe());
        wardrobe.set(0, new LlmRecommendationInput.Item(TOP_ID, evidence, ClothesType.TOP, List.of()));
        var selected = withFeatures(Collections.nCopies(8, feature));
        var boundaryDecision = new LlmOutfitDecision(selected.clothesIds(), selected.analyses(), "r".repeat(1000));

        assertThat(validator.isValid(boundaryDecision, withWardrobe(wardrobe))).isTrue();
    }

    private LlmRecommendationInput withWardrobe(List<LlmRecommendationInput.Item> wardrobe) {
        return new LlmRecommendationInput(input().weather(), 3, input().previousOutfitIds(), wardrobe);
    }

    private boolean validIds(List<UUID> ids) {
        return validator.isValid(new LlmOutfitDecision(ids, ids.stream()
                .map(id -> new LlmOutfitDecision.Analysis(id, List.of())).toList(), "선택"), input());
    }

    private boolean validFeature(LlmOutfitDecision.Feature feature) {
        return validator.isValid(withFeatures(Collections.singletonList(feature)), input());
    }

    private LlmOutfitDecision withFeatures(List<LlmOutfitDecision.Feature> features) {
        return new LlmOutfitDecision(List.of(TOP_ID, BOTTOM_ID), List.of(
                new LlmOutfitDecision.Analysis(TOP_ID, features),
                new LlmOutfitDecision.Analysis(BOTTOM_ID, List.of())), "선택");
    }
}
