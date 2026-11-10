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
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

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

  @Autowired
  private JdbcTemplate jdbcTemplate;

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

  @Test
  void 팔로워_이름_검색과_커서_페이지네이션을_HTTP로_조회한다()
      throws Exception {
    // given
    User followee = createUser("팔로우 대상");
    User otherFollowee = createUser("다른 대상");

    User minsu = createUser("김민수");
    User minji = createUser("이민지");
    User minho = createUser("최민호");
    User seojun = createUser("박서준");

    Instant sameTime = Instant.parse("2026-11-09T03:00:00Z");

    UUID id3 = UUID.fromString(
        "00000000-0000-0000-0000-000000000003"
    );
    UUID id2 = UUID.fromString(
        "00000000-0000-0000-0000-000000000002"
    );
    UUID id1 = UUID.fromString(
        "00000000-0000-0000-0000-000000000001"
    );
    UUID excludedId = UUID.fromString(
        "00000000-0000-0000-0000-000000000004"
    );
    UUID otherId = UUID.fromString(
        "00000000-0000-0000-0000-000000000005"
    );

    insertPaginationFollow(
        id3, minsu.getId(), followee.getId(), sameTime
    );
    insertPaginationFollow(
        id2, minji.getId(), followee.getId(), sameTime
    );
    insertPaginationFollow(
        id1, minho.getId(), followee.getId(), sameTime
    );
    insertPaginationFollow(
        excludedId, seojun.getId(), followee.getId(), sameTime
    );
    insertPaginationFollow(
        otherId, minsu.getId(), otherFollowee.getId(), sameTime
    );

    jdbcTemplate.update("""
        INSERT INTO profiles (
            user_id, profile_image_url,
            created_at, updated_at
        ) VALUES (?, ?, ?, ?)
        """,
        minsu.getId(),
        "https://example.com/minsu.png",
        Timestamp.from(sameTime),
        Timestamp.from(sameTime)
    );

    entityManager.clear();

    String accessToken = accessTokenFor(minsu.getId());

    // when
    var firstPage = mockMvc.perform(
        get("/api/follows/followers")
            .header("Authorization", "Bearer " + accessToken)
            .param("followeeId", followee.getId().toString())
            .param("limit", "2")
            .param("nameLike", "민")
    );

    // then
    firstPage.andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].id")
            .value(id3.toString()))
        .andExpect(jsonPath("$.data[0].follower.userId")
            .value(minsu.getId().toString()))
        .andExpect(jsonPath("$.data[0].follower.name")
            .value("김민수"))
        .andExpect(jsonPath("$.data[0].follower.profileImageUrl")
            .value("https://example.com/minsu.png"))
        .andExpect(jsonPath("$.data[0].followee.userId")
            .value(followee.getId().toString()))
        .andExpect(jsonPath("$.data[1].id")
            .value(id2.toString()))
        .andExpect(jsonPath("$.data[1].follower.name")
            .value("이민지"))
        .andExpect(jsonPath("$.nextCursor")
            .value(sameTime.toString()))
        .andExpect(jsonPath("$.nextIdAfter")
            .value(id2.toString()))
        .andExpect(jsonPath("$.hasNext").value(true))
        .andExpect(jsonPath("$.totalCount").value(3))
        .andExpect(jsonPath("$.sortBy").value("createdAt"))
        .andExpect(jsonPath("$.sortDirection")
            .value("DESCENDING"));

    String responseBody = firstPage
        .andReturn()
        .getResponse()
        .getContentAsString();

    String cursor = JsonPath.read(
        responseBody, "$.nextCursor"
    );
    String idAfter = JsonPath.read(
        responseBody, "$.nextIdAfter"
    );

    // when
    var secondPage = mockMvc.perform(
        get("/api/follows/followers")
            .header("Authorization", "Bearer " + accessToken)
            .param("followeeId", followee.getId().toString())
            .param("limit", "2")
            .param("nameLike", "민")
            .param("cursor", cursor)
            .param("idAfter", idAfter)
    );

    // then
    secondPage.andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id")
            .value(id1.toString()))
        .andExpect(jsonPath("$.data[0].follower.name")
            .value("최민호"))
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.nextCursor").isEmpty())
        .andExpect(jsonPath("$.nextIdAfter").isEmpty())
        .andExpect(jsonPath("$.totalCount").value(3));
  }

  @Test
  void 미인증_사용자는_팔로워_목록을_조회할_수_없다()
      throws Exception {
    // given
    User followee = createUser("팔로우 대상");

    // when
    var result = mockMvc.perform(
        get("/api/follows/followers")
            .param("followeeId", followee.getId().toString())
            .param("limit", "10")
    );

    // then
    result.andExpect(status().isUnauthorized());
  }

  @Test
  void 팔로워_조회에서_잘못된_요청은_400을_반환한다()
      throws Exception {
    // given
    User requester = createUser("요청 사용자");
    User followee = createUser("팔로우 대상");

    String accessToken = accessTokenFor(requester.getId());

    // when
    var missingFolloweeId = mockMvc.perform(
        get("/api/follows/followers")
            .header("Authorization", "Bearer " + accessToken)
            .param("limit", "10")
    );

    var missingLimit = mockMvc.perform(
        get("/api/follows/followers")
            .header("Authorization", "Bearer " + accessToken)
            .param("followeeId", followee.getId().toString())
    );

    var invalidFolloweeId = mockMvc.perform(
        get("/api/follows/followers")
            .header("Authorization", "Bearer " + accessToken)
            .param("followeeId", "invalid-uuid")
            .param("limit", "10")
    );

    var invalidLimit = mockMvc.perform(
        get("/api/follows/followers")
            .header("Authorization", "Bearer " + accessToken)
            .param("followeeId", followee.getId().toString())
            .param("limit", "0")
    );

    var missingIdAfter = mockMvc.perform(
        get("/api/follows/followers")
            .header("Authorization", "Bearer " + accessToken)
            .param("followeeId", followee.getId().toString())
            .param("limit", "10")
            .param("cursor", "2026-11-09T03:00:00Z")
    );

    var invalidCursor = mockMvc.perform(
        get("/api/follows/followers")
            .header("Authorization", "Bearer " + accessToken)
            .param("followeeId", followee.getId().toString())
            .param("limit", "10")
            .param("cursor", "invalid-cursor")
            .param("idAfter", UUID.randomUUID().toString())
    );

    // then
    missingFolloweeId.andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName")
            .value("INVALID_INPUT_VALUE"));

    missingLimit.andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName")
            .value("INVALID_INPUT_VALUE"));

    invalidFolloweeId.andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName")
            .value("INVALID_INPUT_VALUE"));

    invalidLimit.andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName")
            .value("INVALID_INPUT_VALUE"));

    missingIdAfter.andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName")
            .value("INVALID_INPUT_VALUE"));

    invalidCursor.andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName")
            .value("INVALID_INPUT_VALUE"));
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

  private void insertPaginationFollow(
      UUID followId,
      UUID followerId,
      UUID followeeId,
      Instant createdAt
  ) {
    jdbcTemplate.update("""
        INSERT INTO follows (
            id, follower_id, followee_id, created_at
        ) VALUES (?, ?, ?, ?)
        """,
        followId,
        followerId,
        followeeId,
        Timestamp.from(createdAt)
    );
  }
}
