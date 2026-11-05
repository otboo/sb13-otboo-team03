package com.otboo.team03.directmessage.repository;

import com.otboo.team03.directmessage.entity.DirectMessage;
import com.otboo.team03.global.config.JpaConfig;
import com.otboo.team03.global.config.QuerydslConfig;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({JpaConfig.class, QuerydslConfig.class})
@Testcontainers
class DirectMessageRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private DirectMessageRepository directMessageRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void DM_Key로_대화_메시지를_커서_조회한다() {
        User firstUser = userRepository.save(
                new User("first@otboo.io", "encoded-password", "첫 번째 사용자")
        );
        User secondUser = userRepository.save(
                new User("second@otboo.io", "encoded-password", "두 번째 사용자")
        );

        directMessageRepository.save(new DirectMessage(firstUser, secondUser, "첫 번째"));
        directMessageRepository.save(new DirectMessage(secondUser, firstUser, "두 번째"));
        directMessageRepository.save(new DirectMessage(firstUser, secondUser, "세 번째"));

        String dmKey = DirectMessage.createDmKey(firstUser.getId(), secondUser.getId());
        List<DirectMessage> firstPage = directMessageRepository
                .findByDmKeyOrderByCreatedAtDescIdDesc(dmKey, PageRequest.of(0, 2));

        DirectMessage cursorItem = firstPage.get(firstPage.size() - 1);
        List<DirectMessage> nextPage = directMessageRepository.findPageByDmKeyAfterCursor(
                dmKey,
                cursorItem.getCreatedAt(),
                cursorItem.getId(),
                PageRequest.of(0, 2)
        );

        assertThat(firstPage).hasSize(2);
        assertThat(nextPage).hasSize(1);
        assertThat(nextPage).doesNotContainAnyElementsOf(firstPage);
        assertThat(directMessageRepository.countByDmKey(dmKey)).isEqualTo(3);
    }

    @Test
    void 다른_대화의_메시지는_조회하지_않는다() {
        User firstUser = userRepository.save(
                new User("first@otboo.io", "encoded-password", "첫 번째 사용자")
        );
        User secondUser = userRepository.save(
                new User("second@otboo.io", "encoded-password", "두 번째 사용자")
        );
        User thirdUser = userRepository.save(
                new User("third@otboo.io", "encoded-password", "세 번째 사용자")
        );

        directMessageRepository.save(new DirectMessage(firstUser, secondUser, "첫 번째 대화"));
        directMessageRepository.save(new DirectMessage(firstUser, thirdUser, "두 번째 대화"));

        String dmKey = DirectMessage.createDmKey(firstUser.getId(), secondUser.getId());
        List<DirectMessage> messages = directMessageRepository
                .findByDmKeyOrderByCreatedAtDescIdDesc(dmKey, PageRequest.of(0, 20));

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getContent()).isEqualTo("첫 번째 대화");
    }
}
