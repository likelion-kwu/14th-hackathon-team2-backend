package com.likelion.hackathon_be.item.application;

import com.likelion.hackathon_be.common.auth.CurrentUser;
import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.item.domain.Item;
import com.likelion.hackathon_be.item.domain.UserItem;
import com.likelion.hackathon_be.item.dto.ItemResponse;
import com.likelion.hackathon_be.item.repository.ItemRepository;
import com.likelion.hackathon_be.item.repository.UserItemRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItemServiceTests {

    private static final Long USER_ID = 10L;
    private static final Instant ACQUIRED_AT = Instant.parse("2026-08-19T09:55:11Z");

    private ItemRepository itemRepository;
    private UserItemRepository userItemRepository;
    private DefaultItemService service;

    @BeforeEach
    void setUp() {
        itemRepository = mock(ItemRepository.class);
        userItemRepository = mock(UserItemRepository.class);
        service = new DefaultItemService(
                () -> new CurrentUser(USER_ID),
                itemRepository,
                userItemRepository,
                new FixedTimeProvider()
        );
    }

    @Test
    void combinesActiveCatalogWithOwnedAndEquippedState() {
        Item ownedItem = item(31L, "사자 선글라스", "ACCESSORY", "items/accessory/31");
        Item unownedItem = item(32L, "파란 모자", "ACCESSORY", "items/accessory/32");
        UserItem ownership = UserItem.create(USER_ID, 31L, ACQUIRED_AT);
        ownership.setEquipped(true);
        when(itemRepository.findByActiveTrueOrderByIdAsc()).thenReturn(List.of(ownedItem, unownedItem));
        when(userItemRepository.findAllByUserId(USER_ID)).thenReturn(List.of(ownership));

        List<ItemResponse> responses = service.getItems(null, false);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0)).isEqualTo(new ItemResponse(
                31L,
                "사자 선글라스",
                "ACCESSORY",
                "items/accessory/31",
                true,
                true,
                ACQUIRED_AT.atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime()
        ));
        assertThat(responses.get(1).owned()).isFalse();
        assertThat(responses.get(1).equipped()).isFalse();
        assertThat(responses.get(1).acquiredAt()).isNull();
    }

    @Test
    void filtersCatalogByExactStringTypeWithoutAddingEnumPolicy() {
        Item accessory = item(31L, "사자 선글라스", "ACCESSORY", "items/accessory/31");
        when(itemRepository.findByActiveTrueAndItemTypeOrderByIdAsc("ACCESSORY"))
                .thenReturn(List.of(accessory));
        when(userItemRepository.findAllByUserId(USER_ID)).thenReturn(List.of());

        List<ItemResponse> responses = service.getItems("ACCESSORY", false);

        assertThat(responses).extracting(ItemResponse::type).containsExactly("ACCESSORY");
        verify(itemRepository).findByActiveTrueAndItemTypeOrderByIdAsc("ACCESSORY");
    }

    @Test
    void ownedOnlyRemovesUnownedCatalogItems() {
        Item ownedItem = item(31L, "사자 선글라스", "ACCESSORY", "items/accessory/31");
        Item unownedItem = item(32L, "파란 모자", "ACCESSORY", "items/accessory/32");
        UserItem ownership = UserItem.create(USER_ID, 31L, ACQUIRED_AT);
        when(itemRepository.findByActiveTrueOrderByIdAsc()).thenReturn(List.of(ownedItem, unownedItem));
        when(userItemRepository.findAllByUserId(USER_ID)).thenReturn(List.of(ownership));

        List<ItemResponse> responses = service.getItems(null, true);

        assertThat(responses).extracting(ItemResponse::id).containsExactly(31L);
        assertThat(responses.get(0).owned()).isTrue();
    }

    private Item item(Long id, String name, String type, String assetKey) {
        Item item = newInstance(Item.class);
        setField(item, "id", id);
        setField(item, "name", name);
        setField(item, "itemType", type);
        setField(item, "assetKey", assetKey);
        setField(item, "active", true);
        setField(item, "createdAt", ACQUIRED_AT.minusSeconds(60));
        return item;
    }

    private static <T> T newInstance(Class<T> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static class FixedTimeProvider implements TimeProvider {

        @Override
        public Instant now() {
            return ACQUIRED_AT;
        }

        @Override
        public LocalDate todayServiceDate() {
            return LocalDate.of(2026, 8, 19);
        }

        @Override
        public ZoneId serviceZone() {
            return ZoneId.of("Asia/Seoul");
        }
    }
}
