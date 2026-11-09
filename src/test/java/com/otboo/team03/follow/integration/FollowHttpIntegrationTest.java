package com.otboo.team03.follow.integration;

import com.jayway.jsonpath.JsonPath;
import com.otboo.team03.follow.entity.Follow;
import com.otboo.team03.follow.repository.FollowRepository;
import com.otboo.team03.global.security.jwt.JwtRegistry;
import com.otboo.team03.global.security.jwt.JwtTokenProvider;
import com.otboo.team03.support.PostgresTestSupport;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FollowHttpIntegrationTest extends PostgresTestSupport {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private FollowRepository followRepository;

  @Autowired
  private EntityManager entityManager;

  @Autowired
  private JwtTokenProvider jwtTokenProvider;

  @MockitoBean
  private JwtRegistry jwtRegistry;

  @Test
  void 팔로우_생성과_취소가_HTTP와_DB에_정상_반영된다()
      throws Exception {
    // given
    User follower = createUser("팔로워");
    User followee = createUser("팔로우 대상");

    String accessToken = accessTokenFor(follower.getId());

    // when
    var createResult = mockMvc.perform(
        post("/api/follows")
            .with(csrf())
            .header("Authorization", "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson(follower.getId(), followee.getId()))
    );

    // then
    createResult.andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.followee.userId")
            .value(followee.getId().toString()))
        .andExpect(jsonPath("$.followee.name")
            .value("팔로우 대상"))
        .andExpect(jsonPath("$.follower.userId")
            .value(follower.getId().toString()))
        .andExpect(jsonPath("$.follower.name")
            .value("팔로워"));

    String responseBody = createResult
        .andReturn()
        .getResponse()
        .getContentAsString();

    String savedId = JsonPath.read(responseBody, "$.id");
    UUID followId = UUID.fromString(savedId);

    followRepository.flush();
    entityManager.clear();

    Follow savedFollow = followRepository.findById(followId)
        .orElseThrow();

    assertAll(
        () -> assertEquals(
            follower.getId(),
            savedFollow.getFollowerId()
        ),
        () -> assertEquals(
            followee.getId(),
            savedFollow.getFolloweeId()
        ),
        () -> assertNotNull(savedFollow.getCreatedAt())
    );

    // when
    var deleteResult = mockMvc.perform(
        delete("/api/follows/{followId}", followId)
            .with(csrf())
            .header("Authorization", "Bearer " + accessToken)
    );

    // then
    deleteResult.andExpect(status().isNoContent())
        .andExpect(content().string(""));

    followRepository.flush();
    entityManager.clear();

    assertFalse(followRepository.existsById(followId));
  }

  @Test
  void 미인증_사용자는_팔로우를_생성하거나_취소할_수_없다()
      throws Exception {
    // given
    User follower = createUser("팔로워");
    User followee = createUser("팔로우 대상");
    UUID followId = UUID.randomUUID();

    // when
    var createResult = mockMvc.perform(
        post("/api/follows")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson(follower.getId(), followee.getId()))
    );

    var deleteResult = mockMvc.perform(
        delete("/api/follows/{followId}", followId)
            .with(csrf())
    );

    // then
    createResult.andExpect(status().isUnauthorized());
    deleteResult.andExpect(status().isUnauthorized());

    assertFalse(
        followRepository.existsByFollowerIdAndFolloweeId(
            follower.getId(),
            followee.getId()
        )
    );
  }

  @Test
  void 타인_명의_생성과_타인의_팔로우_취소는_403을_반환한다()
      throws Exception {
    // given
    User follower = createUser("팔로워");
    User followee = createUser("팔로우 대상");
    User otherUser = createUser("다른 사용자");

    Follow existingFollow = followRepository.saveAndFlush(
        new Follow(follower.getId(), followee.getId())
    );

    String accessToken = accessTokenFor(otherUser.getId());

    // when
    var createResult = mockMvc.perform(
        post("/api/follows")
            .with(csrf())
            .header("Authorization", "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson(follower.getId(), otherUser.getId()))
    );

    var deleteResult = mockMvc.perform(
        delete("/api/follows/{followId}", existingFollow.getId())
            .with(csrf())
            .header("Authorization", "Bearer " + accessToken)
    );

    // then
    createResult.andExpect(status().isForbidden())
        .andExpect(jsonPath("$.exceptionName")
            .value("ACCESS_DENIED"));

    deleteResult.andExpect(status().isForbidden())
        .andExpect(jsonPath("$.exceptionName")
            .value("ACCESS_DENIED"));

    assertAll(
        () -> assertTrue(
            followRepository.existsById(existingFollow.getId())
        ),
        () -> assertFalse(
            followRepository.existsByFollowerIdAndFolloweeId(
                follower.getId(),
                otherUser.getId()
            )
        )
    );
  }

  @Test
  void 중복_자기자신_없는사용자_없는팔로우_요청을_검증한다()
      throws Exception {
    // given
    User follower = createUser("팔로워");
    User followee = createUser("팔로우 대상");

    followRepository.saveAndFlush(
        new Follow(follower.getId(), followee.getId())
    );

    String accessToken = accessTokenFor(follower.getId());
    UUID nonexistentId = UUID.randomUUID();

    // when
    var duplicateResult = mockMvc.perform(
        post("/api/follows")
            .with(csrf())
            .header("Authorization", "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson(follower.getId(), followee.getId()))
    );

    var selfFollowResult = mockMvc.perform(
        post("/api/follows")
            .with(csrf())
            .header("Authorization", "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson(follower.getId(), follower.getId()))
    );

    var nonexistentUserResult = mockMvc.perform(
        post("/api/follows")
            .with(csrf())
            .header("Authorization", "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson(follower.getId(), nonexistentId))
    );

    var nonexistentFollowResult = mockMvc.perform(
        delete("/api/follows/{followId}", nonexistentId)
            .with(csrf())
            .header("Authorization", "Bearer " + accessToken)
    );

    // then
    duplicateResult.andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName")
            .value("INVALID_INPUT_VALUE"));

    selfFollowResult.andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName")
            .value("INVALID_INPUT_VALUE"));

    nonexistentUserResult.andExpect(status().isNotFound())
        .andExpect(jsonPath("$.exceptionName")
            .value("NOT_FOUND_USER"));

    nonexistentFollowResult.andExpect(status().isNotFound())
        .andExpect(jsonPath("$.exceptionName")
            .value("NOT_FOUND_RESOURCE"));

    assertEquals(
        1L,
        followRepository.count()
    );
  }

  private User createUser(String name) {
    return userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            name
        )
    );
  }

  private String accessTokenFor(UUID userId) {
    String token = jwtTokenProvider.createAccessToken(userId);

    when(jwtRegistry.hasActiveJwtInformationByAccessToken(token))
        .thenReturn(true);

    return token;
  }

  private String requestJson(UUID followerId, UUID followeeId) {
    return """
            {
                "followerId": "%s",
                "followeeId": "%s"
            }
            """.formatted(followerId, followeeId);
  }
}
