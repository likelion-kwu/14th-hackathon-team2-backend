package com.likelion.hackathon_be.item.application;

import com.likelion.hackathon_be.item.domain.Item;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

@Component
public class RandomItemUnlockSelector implements ItemUnlockSelector {

    @Override
    public Optional<Item> select(List<Item> candidates) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        int index = ThreadLocalRandom.current().nextInt(candidates.size());
        return Optional.of(candidates.get(index));
    }
}
