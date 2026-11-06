package com.otboo.team03.profile.service;

import com.otboo.team03.profile.dto.ProfileDto;
import com.otboo.team03.profile.dto.ProfileUpdateRequest;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface ProfileService {
  ProfileDto getProfile(UUID userId);

  ProfileDto updateProfile(UUID userId, ProfileUpdateRequest request, MultipartFile image);

}
