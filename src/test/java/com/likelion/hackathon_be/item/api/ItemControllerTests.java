package com.likelion.hackathon_be.item.api;

import com.likelion.hackathon_be.item.application.ItemService;
import com.likelion.hackathon_be.item.dto.ItemResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ItemControllerTests {

    private ItemService itemService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        itemService = mock(ItemService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ItemController(itemService)).build();
    }

    @Test
    void defaultsOwnedOnlyToFalseAndReturnsItemContract() throws Exception {
        when(itemService.getItems(null, false)).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/v1/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(31))
                .andExpect(jsonPath("$.data[0].type").value("ACCESSORY"))
                .andExpect(jsonPath("$.data[0].assetKey").value("items/accessory/31"))
                .andExpect(jsonPath("$.data[0].owned").value(true))
                .andExpect(jsonPath("$.data[0].equipped").value(true))
                .andExpect(jsonPath("$.data[0].acquiredAt").value("2026-08-17T18:55:11+09:00"));

        verify(itemService).getItems(null, false);
    }

    @Test
    void passesExactTypeAndOwnedOnlyQueryToService() throws Exception {
        when(itemService.getItems("ACCESSORY", true)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/items")
                        .param("type", "ACCESSORY")
                        .param("ownedOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        verify(itemService).getItems("ACCESSORY", true);
    }

    private ItemResponse response() {
        return new ItemResponse(
                31L,
                "사자 선글라스",
                "ACCESSORY",
                "items/accessory/31",
                true,
                true,
                OffsetDateTime.parse("2026-08-17T18:55:11+09:00")
        );
    }
}
