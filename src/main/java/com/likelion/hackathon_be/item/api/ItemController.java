package com.likelion.hackathon_be.item.api;

import com.likelion.hackathon_be.common.api.ApiResponse;
import com.likelion.hackathon_be.item.application.ItemService;
import com.likelion.hackathon_be.item.dto.ItemResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/items")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping
    public ApiResponse<List<ItemResponse>> getItems(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "false") boolean ownedOnly
    ) {
        return ApiResponse.of(itemService.getItems(type, ownedOnly));
    }
}
