package com.otboo.team03.clothes.service;

import com.otboo.team03.clothes.dto.response.ClothesDto;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClothesExtractionService {
    private final ClothesPageClient pageClient;
    private final ClothesHtmlParser parser;

    /** 등록 전 초기값만 반환한다. DB 저장 및 외부 이미지 다운로드는 하지 않는다. */
    public ClothesDto extract(String url, UUID ownerId) {
        var extracted = parser.parse(pageClient.fetch(url));
        return new ClothesDto(null, ownerId, extracted.name(), extracted.imageUrl(), null, List.of());
    }
}
