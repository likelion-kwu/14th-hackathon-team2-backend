package com.likelion.hackathon_be.avatar.dto;

import java.util.List;

public record UpdateAvatarEquipmentResponse(
        List<EquippedItemResponse> equippedItems
) {
}
