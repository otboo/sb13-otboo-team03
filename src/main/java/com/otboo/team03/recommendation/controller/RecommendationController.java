package com.otboo.team03.recommendation.controller;

import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import com.otboo.team03.global.security.OtbooUserDetails;
import com.otboo.team03.recommendation.dto.RecommendationDto;
import com.otboo.team03.recommendation.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService service;

    @GetMapping
    public ResponseEntity<RecommendationDto> getRecommendation(
            @RequestParam UUID weatherId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() ||
                !(authentication.getPrincipal() instanceof OtbooUserDetails userDetails)) {
            throw new OtbooException(ErrorCode.AUTHENTICATION_REQUIRED);
        }

        UUID userId = userDetails.getUserDto().id();
        return ResponseEntity.ok(service.recommend(weatherId, userId));
    }
}
