package com.likelion.hackathon_be.item.application;

import com.likelion.hackathon_be.item.dto.ItemResponse;
import java.util.List;

public interface ItemService {

    List<ItemResponse> getItems(String type, boolean ownedOnly);
}
