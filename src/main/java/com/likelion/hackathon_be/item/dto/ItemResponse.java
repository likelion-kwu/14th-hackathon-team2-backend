package com.likelion.hackathon_be.item.dto;

import java.time.OffsetDateTime;

public record ItemResponse(
        Long id,
        String name,
        String type,
        String assetKey,
        boolean owned,
        boolean equipped,
        OffsetDateTime acquiredAt
) {
}
