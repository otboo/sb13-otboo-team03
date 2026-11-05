package com.otboo.team03.directmessage.service;

import com.otboo.team03.directmessage.dto.response.DirectMessageDto;
import com.otboo.team03.directmessage.entity.DirectMessage;
import com.otboo.team03.directmessage.repository.DirectMessageRepository;
import com.otboo.team03.global.exception.OtbooException;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectMessageServiceTest {

    @Mock
    private DirectMessageRepository directMessageRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DirectMessageService directMessageService;

    @Test
    void DM을_전송한다() {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        User sender = userWithId(senderId);
        User receiver = userWithId(receiverId);
        DirectMessage directMessage = new DirectMessage(sender, receiver, "안녕하세요.");

        when(userRepository.findById(senderId)).thenReturn(Optional.of(sender));
        when(userRepository.findById(receiverId)).thenReturn(Optional.of(receiver));
        when(directMessageRepository.save(any(DirectMessage.class))).thenReturn(directMessage);

        DirectMessageDto result = directMessageService.send(senderId, receiverId, "안녕하세요.");

        assertThat(result.dmKey()).isEqualTo(DirectMessage.createDmKey(senderId, receiverId));
        assertThat(result.senderId()).isEqualTo(senderId);
        assertThat(result.receiverId()).isEqualTo(receiverId);
        assertThat(result.content()).isEqualTo("안녕하세요.");
    }

    @Test
    void 자기_자신에게_DM을_보낼_수_없다() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> directMessageService.send(userId, userId, "메시지"))
                .isInstanceOf(OtbooException.class);
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
        return message;
    }
}
