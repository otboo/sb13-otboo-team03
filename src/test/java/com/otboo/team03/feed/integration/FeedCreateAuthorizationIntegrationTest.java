package com.otboo.team03.feed.integration;

import com.otboo.team03.global.security.jwt.JwtRegistry;
import com.otboo.team03.global.security.jwt.JwtTokenProvider;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FeedCreateAuthorizationIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private JwtTokenProvider jwtTokenProvider;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @MockitoBean
  private JwtRegistry jwtRegistry;

  @Test
  void 유효한_JWT라도_다른_사용자_ID로_피드를_생성하면_403을_반환한다()
      throws Exception {
    // given
    User signedInUser = userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            "로그인 사용자"
        )
    );

    UUID otherUserId = UUID.randomUUID();
    UUID weatherId = UUID.randomUUID();

    String accessToken = jwtTokenProvider.createAccessToken(
        signedInUser.getId()
    );

    when(jwtRegistry.hasActiveJwtInformationByAccessToken(accessToken))
        .thenReturn(true);

    String requestJson = """
                {
                  "authorId": "%s",
                  "weatherId": "%s",
                  "clothesIds": [],
                  "content": "타인 명의 피드"
                }
                """.formatted(otherUserId, weatherId);

    // when
    var result = mockMvc.perform(
        post("/api/feeds")
            .with(csrf())
            .header(
                "Authorization",
                "Bearer " + accessToken
            )
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson)
    );

    // then
    result.andExpect(status().isForbidden())
        .andExpect(jsonPath("$.exceptionName")
            .value("ACCESS_DENIED"));

    Long feedCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM feeds WHERE author_id = ?",
        Long.class,
        otherUserId
    );

    assertEquals(0L, feedCount);
  }
}
