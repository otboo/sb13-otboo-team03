package com.otboo.team03.notification.processed.service;

import com.otboo.team03.notification.processed.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessedEventServiceTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @InjectMocks
    private ProcessedEventService processedEventService;

    @Test
    void 처음_처리한_이벤트는_처리_성공으로_표시한다() {
        String consumerName = "notification-consumer";
        UUID eventId = UUID.randomUUID();
        when(processedEventRepository.insertIfAbsent(
                eq(consumerName), eq(eventId), any()
        )).thenReturn(1);

        boolean result = processedEventService.tryMarkProcessed(consumerName, eventId);

        assertThat(result).isTrue();
        verify(processedEventRepository).insertIfAbsent(
                eq(consumerName), eq(eventId), any()
        );
    }

    @Test
    void 이미_처리한_이벤트는_중복_처리하지_않는다() {
        String consumerName = "notification-consumer";
        UUID eventId = UUID.randomUUID();
        when(processedEventRepository.insertIfAbsent(
                eq(consumerName), eq(eventId), any()
        )).thenReturn(0);

        boolean result = processedEventService.tryMarkProcessed(consumerName, eventId);

        assertThat(result).isFalse();
    }

    @Test
    void Consumer_이름이_비어_있으면_거부한다() {
        assertThatThrownBy(() -> processedEventService.tryMarkProcessed(
                " ", UUID.randomUUID()
        )).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(processedEventRepository);
    }

    @Test
    void 이벤트_ID가_null이면_거부한다() {
        assertThatThrownBy(() -> processedEventService.tryMarkProcessed(
                "notification-consumer", null
        )).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(processedEventRepository);
    }
}
