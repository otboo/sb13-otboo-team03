package com.otboo.team03.follow.mapper;

import com.otboo.team03.follow.dto.response.FollowDto;
import com.otboo.team03.follow.entity.Follow;
import com.otboo.team03.profile.entity.Profile;
import com.otboo.team03.profile.repository.ProfileRepository;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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

  @InjectMocks
  private FollowDtoAssembler followDtoAssembler;

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
        .thenReturn("follower.png");

    when(followeeProfile.getProfileImageUrl())
        .thenReturn("followee.png");

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
        () -> assertEquals("followee.png", result.followee().profileImageUrl()),
        () -> assertEquals(followerId, result.follower().userId()),
        () -> assertEquals("팔로워", result.follower().name()),
        () -> assertEquals("follower.png", result.follower().profileImageUrl())
    );

    verify(userRepository).findById(followeeId);
    verify(userRepository).findById(followerId);
    verify(profileRepository).findById(followeeId);
    verify(profileRepository).findById(followerId);
  }
}
