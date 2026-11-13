package com.otboo.team03.directmessage.service;

import com.otboo.team03.directmessage.dto.response.DirectMessageDto;
import com.otboo.team03.directmessage.entity.DirectMessage;
import com.otboo.team03.directmessage.repository.DirectMessageRepository;
import com.otboo.team03.global.exception.OtbooException;
import com.otboo.team03.notification.entity.NotificationLevel;
import com.otboo.team03.notification.event.NotificationEvent;
import com.otboo.team03.notification.event.NotificationEventType;
import com.otboo.team03.notification.outbox.service.OutboxEventPublisher;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectMessageServiceTest {

    @Mock
    private DirectMessageRepository directMessageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    @InjectMocks
    private DirectMessageService directMessageService;

    @Test
    void DM을_전송한다() {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        User sender = userWithId(senderId);
        User receiver = userWithId(receiverId);
        DirectMessage directMessage = mock(DirectMessage.class);
        String dmKey = DirectMessage.createDmKey(senderId, receiverId);

        when(directMessage.getId()).thenReturn(messageId);
        when(directMessage.getCreatedAt()).thenReturn(createdAt);
        when(directMessage.getSender()).thenReturn(sender);
        when(directMessage.getReceiver()).thenReturn(receiver);
        when(directMessage.getDmKey()).thenReturn(dmKey);
        when(directMessage.getContent()).thenReturn("안녕하세요.");

        when(userRepository.findById(senderId)).thenReturn(Optional.of(sender));
        when(userRepository.findById(receiverId)).thenReturn(Optional.of(receiver));
        when(directMessageRepository.save(any(DirectMessage.class))).thenReturn(directMessage);

        DirectMessageDto result = directMessageService.send(senderId, receiverId, "안녕하세요.");

        assertThat(result.dmKey()).isEqualTo(DirectMessage.createDmKey(senderId, receiverId));
        assertThat(result.senderId()).isEqualTo(senderId);
        assertThat(result.receiverId()).isEqualTo(receiverId);
        assertThat(result.content()).isEqualTo("안녕하세요.");

        ArgumentCaptor<NotificationEvent> eventCaptor =
                ArgumentCaptor.forClass(NotificationEvent.class);
        verify(outboxEventPublisher).publish(
                eventCaptor.capture(),
                eq("DIRECT_MESSAGE"),
                eq(dmKey)
        );

        NotificationEvent event = eventCaptor.getValue();
        assertThat(event.eventType()).isEqualTo(NotificationEventType.DIRECT_MESSAGE_RECEIVED);
        assertThat(event.schemaVersion())
                .isEqualTo(NotificationEvent.CURRENT_SCHEMA_VERSION);
        assertThat(event.aggregateId()).isEqualTo(messageId);
        assertThat(event.receiverId()).isEqualTo(receiverId);
        assertThat(event.actorId()).isEqualTo(senderId);
        assertThat(event.relatedId()).isEqualTo(messageId);
        assertThat(event.title()).isEqualTo("새 메시지");
        assertThat(event.content()).isEqualTo("새로운 메시지가 도착했습니다.");
        assertThat(event.level()).isEqualTo(NotificationLevel.INFO);
        assertThat(event.occurredAt()).isNotNull();
        assertThat(event.eventId()).isNotNull();
    }

    @Test
    void 자기_자신에게_DM을_보낼_수_없다() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> directMessageService.send(userId, userId, "메시지"))
                .isInstanceOf(OtbooException.class);
    }

    @Test
    void 발신자_사용자가_없으면_DM을_전송할_수_없다() {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        when(userRepository.findById(senderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> directMessageService.send(senderId, receiverId, "메시지"))
                .isInstanceOf(RuntimeException.class);

        verifyNoInteractions(directMessageRepository, outboxEventPublisher);
    }

    @Test
    void 수신자_사용자가_없으면_DM을_전송할_수_없다() {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        User sender = mock(User.class);
        when(userRepository.findById(senderId)).thenReturn(Optional.of(sender));
        when(userRepository.findById(receiverId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> directMessageService.send(senderId, receiverId, "메시지"))
                .isInstanceOf(RuntimeException.class);

        verifyNoInteractions(directMessageRepository, outboxEventPublisher);
    }

    @Test
    void DM_저장에_실패하면_알림_이벤트를_발행하지_않는다() {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        User sender = userWithId(senderId);
        User receiver = userWithId(receiverId);

        when(userRepository.findById(senderId)).thenReturn(Optional.of(sender));
        when(userRepository.findById(receiverId)).thenReturn(Optional.of(receiver));
        when(directMessageRepository.save(any(DirectMessage.class)))
                .thenThrow(new IllegalStateException("DM 저장 실패"));

        assertThatThrownBy(() -> directMessageService.send(senderId, receiverId, "메시지"))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(outboxEventPublisher);
    }

    @Test
    void 비어_있는_메시지는_전송할_수_없다() {
        assertThatThrownBy(() -> directMessageService.send(
                UUID.randomUUID(), UUID.randomUUID(), "   "
        )).isInstanceOf(OtbooException.class);
    }

    @Test
    void 발신자_ID가_null이면_DM을_전송할_수_없다() {
        assertThatThrownBy(() -> directMessageService.send(
                null, UUID.randomUUID(), "메시지"
        )).isInstanceOf(OtbooException.class);
    }

    @Test
    void 수신자_ID가_null이면_DM을_전송할_수_없다() {
        assertThatThrownBy(() -> directMessageService.send(
                UUID.randomUUID(), null, "메시지"
        )).isInstanceOf(OtbooException.class);
    }

    @Test
    void 천_자를_초과한_메시지는_전송할_수_없다() {
        assertThatThrownBy(() -> directMessageService.send(
                UUID.randomUUID(), UUID.randomUUID(), "a".repeat(1_001)
        )).isInstanceOf(OtbooException.class);
    }

    @Test
    void 대화_메시지를_조회한다() {
        UUID requesterId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        DirectMessage message = mockMessage(requesterId, otherUserId, "메시지");
        String dmKey = DirectMessage.createDmKey(requesterId, otherUserId);

        when(directMessageRepository.findByDmKeyOrderByCreatedAtDescIdDesc(
                eq(dmKey), any(Pageable.class)
        )).thenReturn(List.of(message));
        when(directMessageRepository.countByDmKey(dmKey)).thenReturn(1L);

        var result = directMessageService.getMessages(
                requesterId, otherUserId, null, null, 20
        );

        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).content()).isEqualTo("메시지");
        assertThat(result.totalCount()).isEqualTo(1L);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void 커서_이후의_대화_메시지를_조회한다() {
        UUID requesterId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Instant cursor = Instant.now();
        UUID idAfter = UUID.randomUUID();
        String dmKey = DirectMessage.createDmKey(requesterId, otherUserId);

        when(directMessageRepository.findPageByDmKeyAfterCursor(
                eq(dmKey), eq(cursor), eq(idAfter), any(Pageable.class)
        )).thenReturn(List.of());
        when(directMessageRepository.countByDmKey(dmKey)).thenReturn(0L);

        var result = directMessageService.getMessages(
                requesterId, otherUserId, cursor, idAfter, 20
        );

        assertThat(result.data()).isEmpty();
        verify(directMessageRepository).findPageByDmKeyAfterCursor(
                eq(dmKey), eq(cursor), eq(idAfter), any(Pageable.class)
        );
    }

    @Test
    void 다음_페이지가_있으면_다음_커서를_반환한다() {
        UUID requesterId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        DirectMessage firstMessage = mockMessage(requesterId, otherUserId, "첫 번째");
        DirectMessage extraMessage = mock(DirectMessage.class);
        String dmKey = DirectMessage.createDmKey(requesterId, otherUserId);

        when(directMessageRepository.findByDmKeyOrderByCreatedAtDescIdDesc(
                eq(dmKey), any(Pageable.class)
        )).thenReturn(List.of(firstMessage, extraMessage));
        when(directMessageRepository.countByDmKey(dmKey)).thenReturn(2L);

        var result = directMessageService.getMessages(
                requesterId, otherUserId, null, null, 1
        );

        assertThat(result.data()).hasSize(1);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isEqualTo(firstMessage.getCreatedAt().toString());
        assertThat(result.nextIdAfter()).isEqualTo(firstMessage.getId());
    }

    @Test
    void 조회_limit이_0이면_거부한다() {
        assertThatThrownBy(() -> directMessageService.getMessages(
                UUID.randomUUID(), UUID.randomUUID(), null, null, 0
        )).isInstanceOf(OtbooException.class);
    }

    @Test
    void 요청자와_상대방이_같으면_대화를_조회할_수_없다() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> directMessageService.getMessages(
                userId, userId, null, null, 20
        )).isInstanceOf(OtbooException.class);
    }

    @Test
    void 요청자_ID가_null이면_대화를_조회할_수_없다() {
        assertThatThrownBy(() -> directMessageService.getMessages(
                null, UUID.randomUUID(), null, null, 20
        )).isInstanceOf(OtbooException.class);
    }

    @Test
    void 상대방_ID가_null이면_대화를_조회할_수_없다() {
        assertThatThrownBy(() -> directMessageService.getMessages(
                UUID.randomUUID(), null, null, null, 20
        )).isInstanceOf(OtbooException.class);
    }

    @Test
    void 커서와_ID가_짝지어_전달되지_않으면_거부한다() {
        assertThatThrownBy(() -> directMessageService.getMessages(
                UUID.randomUUID(), UUID.randomUUID(), Instant.now(), null, 20
        )).isInstanceOf(OtbooException.class);
    }

    private User userWithId(UUID id) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        return user;
    }

    private DirectMessage mockMessage(UUID senderId, UUID receiverId, String content) {
        DirectMessage message = mock(DirectMessage.class);
        User sender = userWithId(senderId);
        User receiver = userWithId(receiverId);
        when(message.getSender()).thenReturn(sender);
        when(message.getReceiver()).thenReturn(receiver);
        when(message.getContent()).thenReturn(content);
        when(message.getDmKey()).thenReturn(DirectMessage.createDmKey(senderId, receiverId));
        when(message.getId()).thenReturn(UUID.randomUUID());
        when(message.getCreatedAt()).thenReturn(Instant.now());
        return message;
    }
}
