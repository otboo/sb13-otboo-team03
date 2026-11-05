package com.otboo.team03.directmessage.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DirectMessageTest {

    @Test
    void 두_사용자_ID를_오름차순으로_정렬해_DM_Key를_생성한다() {
        UUID firstUserId = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        UUID secondUserId = UUID.fromString("00000000-0000-0000-0000-000000000000");

        String dmKey = DirectMessage.createDmKey(firstUserId, secondUserId);

        assertThat(dmKey).isEqualTo(secondUserId + "_" + firstUserId);
        assertThat(dmKey).hasSize(73);
    }

    @Test
    void 같은_두_사용자의_DM_Key는_입력_순서와_관계없이_같다() {
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();

        assertThat(DirectMessage.createDmKey(firstUserId, secondUserId))
                .isEqualTo(DirectMessage.createDmKey(secondUserId, firstUserId));
    }
}
