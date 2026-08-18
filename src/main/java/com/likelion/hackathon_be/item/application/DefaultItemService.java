package com.likelion.hackathon_be.item.application;

import com.likelion.hackathon_be.common.auth.CurrentUserProvider;
import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.item.domain.Item;
import com.likelion.hackathon_be.item.domain.UserItem;
import com.likelion.hackathon_be.item.dto.ItemResponse;
import com.likelion.hackathon_be.item.repository.ItemRepository;
import com.likelion.hackathon_be.item.repository.UserItemRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultItemService implements ItemService {

    private final CurrentUserProvider currentUserProvider;
    private final ItemRepository itemRepository;
    private final UserItemRepository userItemRepository;
    private final TimeProvider timeProvider;

    public DefaultItemService(
            CurrentUserProvider currentUserProvider,
            ItemRepository itemRepository,
            UserItemRepository userItemRepository,
            TimeProvider timeProvider
    ) {
        this.currentUserProvider = currentUserProvider;
        this.itemRepository = itemRepository;
        this.userItemRepository = userItemRepository;
        this.timeProvider = timeProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemResponse> getItems(String type, boolean ownedOnly) {
        Long userId = currentUserProvider.getCurrentUser().id();
        Map<Long, UserItem> ownedItemByItemId = userItemRepository.findAllByUserId(userId)
                .stream()
                .collect(Collectors.toMap(UserItem::getItemId, Function.identity()));

        return activeItems(type).stream()
                .filter(item -> !ownedOnly || ownedItemByItemId.containsKey(item.getId()))
                .map(item -> toResponse(item, ownedItemByItemId.get(item.getId())))
                .toList();
    }

    private List<Item> activeItems(String type) {
        if (type == null) {
            return itemRepository.findByActiveTrueOrderByIdAsc();
        }
        return itemRepository.findByActiveTrueAndItemTypeOrderByIdAsc(type);
    }

    private ItemResponse toResponse(Item item, UserItem userItem) {
        boolean owned = userItem != null;
        return new ItemResponse(
                item.getId(),
                item.getName(),
                item.getItemType(),
                item.getAssetKey(),
                owned,
                owned && userItem.isEquipped(),
                owned ? toOffsetDateTime(userItem) : null
        );
    }

    private OffsetDateTime toOffsetDateTime(UserItem userItem) {
        return userItem.getAcquiredAt().atZone(timeProvider.serviceZone()).toOffsetDateTime();
    }
}
