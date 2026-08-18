package com.likelion.hackathon_be.item.application;

import com.likelion.hackathon_be.common.error.FeatureNotImplementedException;
import com.likelion.hackathon_be.item.dto.ItemResponse;
import java.util.List;

public class NotImplementedItemService implements ItemService {

    @Override
    public List<ItemResponse> getItems(String type, boolean ownedOnly) {
        throw new FeatureNotImplementedException("Item");
    }
}
