package com.otboo.team03.follow.mapper;

import com.otboo.team03.follow.dto.response.FollowDto;
import com.otboo.team03.follow.dto.response.UserSummary;
import com.otboo.team03.follow.entity.Follow;
import com.otboo.team03.global.storage.ImageStorage;
import com.otboo.team03.profile.entity.Profile;
import com.otboo.team03.profile.repository.ProfileRepository;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.exception.UserNotFoundException;
import com.otboo.team03.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FollowDtoAssembler {

  private final UserRepository userRepository;
  private final ProfileRepository profileRepository;
  private final Optional<ImageStorage> imageStorage;

  public FollowDto toDto(Follow follow) {
    return new FollowDto(
        follow.getId(),
        toUserSummary(follow.getFolloweeId()),
        toUserSummary(follow.getFollowerId())
    );
  }

  private UserSummary toUserSummary(UUID userId) {
    User user = userRepository.findById(userId)
        .orElseThrow(UserNotFoundException::new);

    String profileImageUrl = profileRepository.findById(userId)
        .map(Profile::getProfileImageUrl)
        .map(this::resolveImageUrl)
        .orElse(null);

    return new UserSummary(
        user.getId(),
        user.getName(),
        profileImageUrl
    );
  }

  private String resolveImageUrl(String raw) {
    if (raw == null) {
      return null;
    }

    if (raw.startsWith("http://")
        || raw.startsWith("https://")) {
      return raw;
    }

    return imageStorage
        .map(storage -> storage.getAccessUrl(raw))
        .orElse(null);
  }
}
