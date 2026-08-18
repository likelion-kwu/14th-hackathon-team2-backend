package com.likelion.hackathon_be.avatar.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateAvatarEquipmentRequest(
        @NotNull
        List<@NotNull Long> equippedItemIds
) {
}
