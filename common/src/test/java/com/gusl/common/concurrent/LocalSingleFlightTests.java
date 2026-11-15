package com.gusl.common.concurrent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalSingleFlightTests {

    @Test
    void sameKeyConcurrentRequestsShareOneLoad() throws Exception {
        LocalSingleFlight<String, String> singleFlight = new LocalSingleFlight<>();
        AtomicInteger loadCount = new AtomicInteger();
        CountDownLatch requestsReady = new CountDownLatch(8);
        CountDownLatch startRequests = new CountDownLatch(1);
        CountDownLatch loaderStarted = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<CompletableFuture<String>> requests = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                requests.add(CompletableFuture.supplyAsync(
                        () -> {
                            requestsReady.countDown();
                            await(startRequests);
                            return singleFlight.execute("contest:1", () -> {
                                loadCount.incrementAndGet();
                                loaderStarted.countDown();
                                await(releaseLoader);
                                return "snapshot";
                            });
                        },
                        executor
                ));
            }

            assertTrue(requestsReady.await(2, TimeUnit.SECONDS));
            startRequests.countDown();
            assertTrue(loaderStarted.await(2, TimeUnit.SECONDS));
            // 给已经越过闸门的请求进入 putIfAbsent，确保它们属于同一加载批次。
            Thread.sleep(200);
            releaseLoader.countDown();

            for (CompletableFuture<String> request : requests) {
                assertEquals("snapshot", request.get(2, TimeUnit.SECONDS));
            }
            assertEquals(1, loadCount.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void failedLoadIsBroadcastAndNextRequestCanRetry() {
        LocalSingleFlight<String, String> singleFlight = new LocalSingleFlight<>();
        AtomicInteger loadCount = new AtomicInteger();

        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> singleFlight.execute("contest:1", () -> {
                    loadCount.incrementAndGet();
                    throw new IllegalStateException("database unavailable");
                })
        );

        assertEquals("database unavailable", exception.getCause().getMessage());
        assertEquals("recovered", singleFlight.execute("contest:1", () -> {
            loadCount.incrementAndGet();
            return "recovered";
        }));
        assertEquals(2, loadCount.get());
    }

    @Test
    void differentKeysCanLoadIndependently() {
        LocalSingleFlight<String, String> singleFlight = new LocalSingleFlight<>();
        AtomicInteger loadCount = new AtomicInteger();

        assertEquals("one", singleFlight.execute("one", () -> {
            loadCount.incrementAndGet();
            return "one";
        }));
        assertEquals("two", singleFlight.execute("two", () -> {
            loadCount.incrementAndGet();
            return "two";
        }));

        assertEquals(2, loadCount.get());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("测试加载线程被中断", exception);
        }
    }
}
