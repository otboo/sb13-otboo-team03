package com.otboo.team03.global.dto;

import java.util.List;
import java.util.UUID;

public record CursorResponse<T>(
        List<T> data,
        String nextCursor,
        UUID nextIdAfter,
        boolean hasNext,
        long totalCount,
        String sortBy,
        String sortDirection
) {
}