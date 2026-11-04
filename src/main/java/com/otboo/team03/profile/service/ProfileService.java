package com.otboo.team03.profile.service;

import com.otboo.team03.profile.dto.ProfileDto;
import java.util.UUID;

public interface ProfileService {
  ProfileDto getProfile(UUID userId);
}
