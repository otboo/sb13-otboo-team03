
package com.otboo.team03.follow.mapper;

import com.otboo.team03.follow.dto.response.FollowDto;
import com.otboo.team03.follow.entity.Follow;
import com.otboo.team03.global.storage.ImageStorage;
import com.otboo.team03.profile.entity.Profile;
import com.otboo.team03.profile.repository.ProfileRepository;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FollowDtoAssemblerTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private ProfileRepository profileRepository;

  @Mock
  private ImageStorage imageStorage;

  private FollowDtoAssembler followDtoAssembler;

  @BeforeEach
  void setUp() {
    followDtoAssembler = new FollowDtoAssembler(
        userRepository,
        profileRepository,
        Optional.of(imageStorage)
    );
  }

  @Test
  void 팔로우_응답에_사용자_이름과_프로필_이미지가_포함된다() {
    // given
    UUID followId = UUID.randomUUID();
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();

    Follow follow = mock(Follow.class);

    when(follow.getId()).thenReturn(followId);
    when(follow.getFollowerId()).thenReturn(followerId);
    when(follow.getFolloweeId()).thenReturn(followeeId);

    User follower = mock(User.class);
    User followee = mock(User.class);

    when(follower.getId()).thenReturn(followerId);
    when(follower.getName()).thenReturn("팔로워");

    when(followee.getId()).thenReturn(followeeId);
    when(followee.getName()).thenReturn("팔로우 대상");

    Profile followerProfile = mock(Profile.class);
    Profile followeeProfile = mock(Profile.class);

    when(followerProfile.getProfileImageUrl())
        .thenReturn("https://example.com/follower.png");

    when(followeeProfile.getProfileImageUrl())
        .thenReturn("https://example.com/followee.png");

    when(userRepository.findById(followerId))
        .thenReturn(Optional.of(follower));

    when(userRepository.findById(followeeId))
        .thenReturn(Optional.of(followee));

    when(profileRepository.findById(followerId))
        .thenReturn(Optional.of(followerProfile));

    when(profileRepository.findById(followeeId))
        .thenReturn(Optional.of(followeeProfile));

    // when
    FollowDto result = followDtoAssembler.toDto(follow);

    // then
    assertAll(
        () -> assertEquals(followId, result.id()),
        () -> assertEquals(followeeId, result.followee().userId()),
        () -> assertEquals("팔로우 대상", result.followee().name()),
        () -> assertEquals(
            "https://example.com/followee.png",
            result.followee().profileImageUrl()
        ),
        () -> assertEquals(followerId, result.follower().userId()),
        () -> assertEquals("팔로워", result.follower().name()),
        () -> assertEquals(
            "https://example.com/follower.png",
            result.follower().profileImageUrl()
        )
    );

    verify(userRepository).findById(followeeId);
    verify(userRepository).findById(followerId);
    verify(profileRepository).findById(followeeId);
    verify(profileRepository).findById(followerId);
    verifyNoInteractions(imageStorage);
  }

  @Test
  void 프로필이_없는_사용자는_프로필_이미지를_null로_반환한다() {
    // given
    UUID followId = UUID.randomUUID();
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();

    Follow follow = mock(Follow.class);

    when(follow.getId()).thenReturn(followId);
    when(follow.getFollowerId()).thenReturn(followerId);
    when(follow.getFolloweeId()).thenReturn(followeeId);

    User follower = mock(User.class);
    User followee = mock(User.class);

    when(follower.getId()).thenReturn(followerId);
    when(follower.getName()).thenReturn("팔로워");

    when(followee.getId()).thenReturn(followeeId);
    when(followee.getName()).thenReturn("팔로우 대상");

    when(userRepository.findById(followerId))
        .thenReturn(Optional.of(follower));

    when(userRepository.findById(followeeId))
        .thenReturn(Optional.of(followee));

    when(profileRepository.findById(followerId))
        .thenReturn(Optional.empty());

    when(profileRepository.findById(followeeId))
        .thenReturn(Optional.empty());

    // when
    FollowDto result = followDtoAssembler.toDto(follow);

    // then
    assertAll(
        () -> assertEquals(followId, result.id()),
        () -> assertEquals(followerId, result.follower().userId()),
        () -> assertEquals("팔로워", result.follower().name()),
        () -> assertNull(result.follower().profileImageUrl()),
        () -> assertEquals(followeeId, result.followee().userId()),
        () -> assertEquals("팔로우 대상", result.followee().name()),
        () -> assertNull(result.followee().profileImageUrl())
    );

    verify(profileRepository).findById(followerId);
    verify(profileRepository).findById(followeeId);
    verifyNoInteractions(imageStorage);
  }

  @Test
  void S3_객체_키를_접근_URL로_변환하여_반환한다() {
    // given
    UUID followId = UUID.randomUUID();
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();

    Follow follow = mock(Follow.class);

    when(follow.getId()).thenReturn(followId);
    when(follow.getFollowerId()).thenReturn(followerId);
    when(follow.getFolloweeId()).thenReturn(followeeId);

    User follower = mock(User.class);
    User followee = mock(User.class);

    when(follower.getId()).thenReturn(followerId);
    when(follower.getName()).thenReturn("팔로워");

    when(followee.getId()).thenReturn(followeeId);
    when(followee.getName()).thenReturn("팔로우 대상");

    Profile followerProfile = mock(Profile.class);
    Profile followeeProfile = mock(Profile.class);

    when(followerProfile.getProfileImageUrl())
        .thenReturn("clothes/follower.png");

    when(followeeProfile.getProfileImageUrl())
        .thenReturn("clothes/followee.png");

    when(userRepository.findById(followerId))
        .thenReturn(Optional.of(follower));

    when(userRepository.findById(followeeId))
        .thenReturn(Optional.of(followee));

    when(profileRepository.findById(followerId))
        .thenReturn(Optional.of(followerProfile));

    when(profileRepository.findById(followeeId))
        .thenReturn(Optional.of(followeeProfile));

    when(imageStorage.getAccessUrl("clothes/follower.png"))
        .thenReturn("https://example.com/follower.png");

    when(imageStorage.getAccessUrl("clothes/followee.png"))
        .thenReturn("https://example.com/followee.png");

    // when
    FollowDto result = followDtoAssembler.toDto(follow);

    // then
    assertAll(
        () -> assertEquals(
            "https://example.com/follower.png",
            result.follower().profileImageUrl()
        ),
        () -> assertEquals(
            "https://example.com/followee.png",
            result.followee().profileImageUrl()
        )
    );

    verify(imageStorage).getAccessUrl("clothes/follower.png");
    verify(imageStorage).getAccessUrl("clothes/followee.png");
  }

  @Test
  void 팔로워_목록은_사용자와_프로필을_각각_한번만_일괄_조회한다() {
    // given
    UUID followeeId = UUID.randomUUID();
    UUID followerAId = UUID.randomUUID();
    UUID followerBId = UUID.randomUUID();
    UUID firstFollowId = UUID.randomUUID();
    UUID secondFollowId = UUID.randomUUID();

    Follow first = mock(Follow.class);
    Follow second = mock(Follow.class);

    when(first.getId()).thenReturn(firstFollowId);
    when(first.getFolloweeId()).thenReturn(followeeId);
    when(first.getFollowerId()).thenReturn(followerAId);

    when(second.getId()).thenReturn(secondFollowId);
    when(second.getFolloweeId()).thenReturn(followeeId);
    when(second.getFollowerId()).thenReturn(followerBId);

    User followee = mock(User.class);
    User followerA = mock(User.class);
    User followerB = mock(User.class);

    when(followee.getId()).thenReturn(followeeId);
    when(followee.getName()).thenReturn("팔로우 대상");

    when(followerA.getId()).thenReturn(followerAId);
    when(followerA.getName()).thenReturn("김민수");

    when(followerB.getId()).thenReturn(followerBId);
    when(followerB.getName()).thenReturn("이민지");

    when(userRepository.findAllById(any()))
        .thenReturn(List.of(followee, followerA, followerB));

    Profile followeeProfile = mock(Profile.class);

    when(followeeProfile.getUserId()).thenReturn(followeeId);
    when(followeeProfile.getProfileImageUrl())
        .thenReturn("profiles/followee.png");

    when(profileRepository.findAllById(any()))
        .thenReturn(List.of(followeeProfile));

    when(imageStorage.getAccessUrl("profiles/followee.png"))
        .thenReturn("https://example.com/followee.png");

    // when
    List<FollowDto> result = followDtoAssembler.toDtos(
        List.of(first, second)
    );

    // then
    assertAll(
        () -> assertEquals(2, result.size()),
        () -> assertEquals(firstFollowId, result.get(0).id()),
        () -> assertEquals(secondFollowId, result.get(1).id()),
        () -> assertEquals(
            "김민수",
            result.get(0).follower().name()
        ),
        () -> assertEquals(
            "이민지",
            result.get(1).follower().name()
        ),
        () -> assertEquals(
            "https://example.com/followee.png",
            result.get(0).followee().profileImageUrl()
        ),
        () -> assertNull(
            result.get(0).follower().profileImageUrl()
        ),
        () -> assertNull(
            result.get(1).follower().profileImageUrl()
        ),
        () -> assertSame(
            result.get(0).followee(),
            result.get(1).followee()
        )
    );

    Set<UUID> expectedIds = Set.of(
        followeeId,
        followerAId,
        followerBId
    );

    verify(userRepository, times(1)).findAllById(expectedIds);
    verify(profileRepository, times(1)).findAllById(expectedIds);

    verify(userRepository, never()).findById(any(UUID.class));
    verify(profileRepository, never()).findById(any(UUID.class));

    verify(imageStorage, times(1))
        .getAccessUrl("profiles/followee.png");
  }

  @Test
  void 빈_팔로워_목록은_사용자와_프로필을_조회하지_않는다() {
    // given
    List<Follow> follows = List.of();

    // when
    List<FollowDto> result = followDtoAssembler.toDtos(follows);

    // then
    assertTrue(result.isEmpty());

    verifyNoInteractions(
        userRepository,
        profileRepository,
        imageStorage
    );
  }
}
