package com.otboo.team03.profile.controller;

import com.otboo.team03.profile.dto.ProfileDto;
import com.otboo.team03.profile.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/users/{userId}/profiles")
@RequiredArgsConstructor
public class ProfileController {

  private final ProfileService profileService;

  @GetMapping
  public ResponseEntity<ProfileDto> getProfile(@PathVariable UUID userId) {
    ProfileDto response = profileService.getProfile(userId);
    return ResponseEntity.ok(response);
  }

}
