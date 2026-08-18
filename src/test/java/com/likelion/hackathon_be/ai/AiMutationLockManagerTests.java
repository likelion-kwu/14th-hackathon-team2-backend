package com.likelion.hackathon_be.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiMutationLockManagerTests {
    @Test
    void neverRunsTwoMutationsForTheSameUserConcurrentlyUnderContention() throws Exception {
        AiMutationLockManager manager = new AiMutationLockManager();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(12);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < 1_000; index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    manager.withUserLock(7L, () -> {
                        int current = active.incrementAndGet();
                        maximum.accumulateAndGet(current, Math::max);
                        Thread.yield();
                        active.decrementAndGet();
                        return null;
                    });
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(maximum).hasValue(1);
    }

    @Test
    void supportsNestedMutationForTheSameUser() {
        AiMutationLockManager manager = new AiMutationLockManager();

        String result = manager.withUserLock(9L, () -> manager.withUserLock(9L, () -> "ok"));

        assertThat(result).isEqualTo("ok");
    }
}
