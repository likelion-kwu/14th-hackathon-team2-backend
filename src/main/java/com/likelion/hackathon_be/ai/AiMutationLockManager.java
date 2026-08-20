package com.likelion.hackathon_be.ai;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

@Component
public class AiMutationLockManager {
    private final ConcurrentHashMap<Long, LockEntry> locks = new ConcurrentHashMap<>();

    public <T> T withUserLock(Long userId, Supplier<T> work) {
        LockEntry entry = locks.compute(userId, (ignored, current) -> {
            LockEntry selected = current == null ? new LockEntry() : current;
            selected.references.incrementAndGet();
            return selected;
        });
        entry.lock.lock();
        try {
            return work.get();
        } finally {
            entry.lock.unlock();
            locks.compute(userId, (ignored, current) -> {
                int remaining = entry.references.decrementAndGet();
                return current == entry && remaining == 0 ? null : current;
            });
        }
    }

    private static final class LockEntry {
        private final ReentrantLock lock = new ReentrantLock();
        private final AtomicInteger references = new AtomicInteger();
    }
}
