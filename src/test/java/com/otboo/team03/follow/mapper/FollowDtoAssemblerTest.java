
package com.otboo.team03.follow.mapper;

import com.otboo.team03.follow.dto.response.FollowDto;
import com.otboo.team03.follow.entity.Follow;
import com.otboo.team03.global.storage.ImageStorage;
import com.otboo.team03.profile.entity.Profile;
import com.otboo.team03.profile.repository.ProfileRepository;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
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
}
