package com.otboo.team03.profile.converter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StringListJsonConverterTest {

  private final StringListJsonConverter converter = new StringListJsonConverter();

  @Test
  @DisplayName("List<String>을 JSON 문자열로 변환한다")
  void convertToDatabaseColumn_success() {
    List<String> list = List.of("서울특별시", "중구");
    String json = converter.convertToDatabaseColumn(list);
    assertThat(json).contains("서울특별시", "중구");
  }

  @Test
  @DisplayName("null 또는 빈 리스트는 빈 JSON 배열([])로 변환한다")
  void convertToDatabaseColumn_emptyOrNull() {
    assertThat(converter.convertToDatabaseColumn(null)).isEqualTo("[]");
    assertThat(converter.convertToDatabaseColumn(List.of())).isEqualTo("[]");
  }

  @Test
  @DisplayName("JSON 문자열을 List<String>으로 역직렬화한다")
  void convertToEntityAttribute_success() {
    String json = "[\"서울특별시\",\"중구\"]";
    List<String> result = converter.convertToEntityAttribute(json);
    assertThat(result).containsExactly("서울특별시", "중구");
  }

  @Test
  @DisplayName("null, 빈 문자열, 잘못된 JSON은 빈 리스트를 반환한다")
  void convertToEntityAttribute_emptyOrInvalid() {
    assertThat(converter.convertToEntityAttribute(null)).isEmpty();
    assertThat(converter.convertToEntityAttribute("   ")).isEmpty();
    assertThat(converter.convertToEntityAttribute("invalid json")).isEmpty();
  }

}
