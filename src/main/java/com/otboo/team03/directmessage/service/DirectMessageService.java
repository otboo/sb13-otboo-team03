package com.otboo.team03.directmessage.service;

import com.otboo.team03.directmessage.dto.response.DirectMessageDto;
import com.otboo.team03.directmessage.entity.DirectMessage;
import com.otboo.team03.directmessage.repository.DirectMessageRepository;
import com.otboo.team03.global.dto.CursorResponse;
import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.exception.UserNotFoundException;
import com.otboo.team03.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DirectMessageService {

    private static final String SORT_BY_CREATED_AT = "createdAt";
    private static final String SORT_DIRECTION_DESCENDING = "DESCENDING";

    private final DirectMessageRepository directMessageRepository;
    private final UserRepository userRepository;

    @Transactional
    public DirectMessageDto send(UUID senderId, UUID receiverId, String content) {
        validateSendRequest(senderId, receiverId, content);

        User sender = userRepository.findById(senderId)
                .orElseThrow(UserNotFoundException::new);
        User receiver = userRepository.findById(receiverId)
                .orElseThrow(UserNotFoundException::new);

        DirectMessage saved = directMessageRepository.save(
                new DirectMessage(sender, receiver, content)
        );
        return DirectMessageDto.from(saved);
    }

    @Transactional(readOnly = true)
    public CursorResponse<DirectMessageDto> getMessages(
            UUID requesterId,
            UUID otherUserId,
            Instant cursor,
            UUID idAfter,
            int limit
    ) {
        validateListRequest(requesterId, otherUserId, cursor, idAfter, limit);

        String dmKey = DirectMessage.createDmKey(requesterId, otherUserId);
        PageRequest pageRequest = PageRequest.of(0, limit + 1);
        List<DirectMessage> messages = cursor == null
                ? directMessageRepository.findByDmKeyOrderByCreatedAtDescIdDesc(
                dmKey,
                pageRequest
        )
                : directMessageRepository.findPageByDmKeyAfterCursor(
                dmKey,
                cursor,
                idAfter,
                pageRequest
        );

        boolean hasNext = messages.size() > limit;
        List<DirectMessage> page = hasNext
                ? messages.subList(0, limit)
                : messages;
        List<DirectMessageDto> data = page.stream()
                .map(DirectMessageDto::from)
                .toList();

        DirectMessage lastMessage = page.isEmpty()
                ? null
                : page.get(page.size() - 1);

        return new CursorResponse<>(
                data,
                hasNext && lastMessage != null
                        ? lastMessage.getCreatedAt().toString()
                        : null,
                hasNext && lastMessage != null
                        ? lastMessage.getId()
                        : null,
                hasNext,
                directMessageRepository.countByDmKey(dmKey),
                SORT_BY_CREATED_AT,
                SORT_DIRECTION_DESCENDING
        );
    }

    private void validateSendRequest(UUID senderId, UUID receiverId, String content) {
        if (senderId == null
                || receiverId == null
                || senderId.equals(receiverId)
                || content == null
                || content.isBlank()
                || content.length() > 1_000) {
            throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateListRequest(
            UUID requesterId,
            UUID otherUserId,
            Instant cursor,
            UUID idAfter,
            int limit
    ) {
        if (requesterId == null
                || otherUserId == null
                || requesterId.equals(otherUserId)
                || limit <= 0
                || (cursor == null) != (idAfter == null)) {
            throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
