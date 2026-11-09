package com.otboo.team03.directmessage.websocket;

import com.otboo.team03.directmessage.entity.DirectMessage;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DirectMessageDestinationValidatorTest {

    private final DirectMessageDestinationValidator validator =
            new DirectMessageDestinationValidator();

    @Test
    void 대화_참여자는_DM_구독이_가능하다() {
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        String destination = "/sub/direct-messages_"
                + DirectMessage.createDmKey(firstUserId, secondUserId);

        assertThat(validator.isParticipant(firstUserId, destination)).isTrue();
        assertThat(validator.isParticipant(secondUserId, destination)).isTrue();
    }

    @Test
    void 대화에_참여하지_않은_사용자는_DM을_구독할_수_없다() {
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        UUID outsiderId = UUID.randomUUID();
        String destination = "/sub/direct-messages_"
                + DirectMessage.createDmKey(firstUserId, secondUserId);

        assertThat(validator.isParticipant(outsiderId, destination)).isFalse();
    }

    @Test
    void 잘못된_DM_구독_주소는_거부한다() {
        assertThat(validator.isParticipant(
                UUID.randomUUID(), "/sub/direct-messages_invalid"
        )).isFalse();
    }
}
