package com.likelion.hackathon_be.item.application;

import com.likelion.hackathon_be.item.domain.Item;
import java.util.List;
import java.util.Optional;

public interface ItemUnlockSelector {

    Optional<Item> select(List<Item> candidates);
}
