package com.likelion.hackathon_be.item.application;

import com.likelion.hackathon_be.common.error.FeatureNotImplementedException;
import com.likelion.hackathon_be.item.dto.ItemResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class NotImplementedItemService implements ItemService {

    @Override
    public List<ItemResponse> getItems(String type, boolean ownedOnly) {
        throw new FeatureNotImplementedException("Item");
    }
}
