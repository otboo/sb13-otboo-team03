package com.otboo.team03.follow.dto.request;

import java.util.UUID;

public record FollowCreateRequest(
    UUID followerId,
    UUID followeeId
) {
}
