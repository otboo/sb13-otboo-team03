package com.otboo.team03.notification.processed.service;

import com.otboo.team03.notification.processed.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProcessedEventService {

    private final ProcessedEventRepository processedEventRepository;

    /**
     * 이벤트가 처음 처리되었으면 처리 이력을 저장하고 true를 반환한다.
     * 동일한 consumer와 eventId 조합이 이미 존재하면 false를 반환한다.
     */
    @Transactional
    public boolean tryMarkProcessed(String consumerName, UUID eventId) {
        validate(consumerName, eventId);

        return processedEventRepository.insertIfAbsent(
                consumerName,
                eventId,
                Instant.now()
        ) == 1;
    }

    private void validate(String consumerName, UUID eventId) {
        if (consumerName == null || consumerName.isBlank() || eventId == null) {
            throw new IllegalArgumentException("consumerName과 eventId는 필수입니다.");
        }
    }
}
