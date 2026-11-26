package com.otboo.team03.follow.dto.response;

import java.util.UUID;

public record FollowSummaryDto(
    UUID followeeId,
    long followerCount,
    long followingCount,
    boolean followedByMe,
    UUID followedByMeId,
    boolean followingMe
) {
}
