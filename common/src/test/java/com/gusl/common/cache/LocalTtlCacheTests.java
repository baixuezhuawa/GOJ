package com.gusl.common.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LocalTtlCacheTests {

    @Test
    void returnsValueWithinTtl() {
        LocalTtlCache<String, String> cache = new LocalTtlCache<>();
        cache.put("rank:1", "snapshot", Duration.ofMinutes(1));

        assertEquals("snapshot", cache.get("rank:1"));
    }

    @Test
    void returnsNullAfterTtlExpires() {
        LocalTtlCache<String, String> cache = new LocalTtlCache<>();
        cache.put("rank:1", "snapshot", Duration.ZERO);

        assertNull(cache.get("rank:1"));
    }

    @Test
    void keepsLocalCacheBoundedByConfiguredCapacity() {
        LocalTtlCache<String, String> cache = new LocalTtlCache<>(1);
        cache.put("rank:1", "first", Duration.ofMinutes(1));
        cache.put("rank:2", "second", Duration.ofMinutes(1));

        // 本地缓存只保证容量有界，不保证淘汰策略顺序。
        assertEquals(1, (cache.get("rank:1") == null ? 0 : 1)
                + (cache.get("rank:2") == null ? 0 : 1));
    }
}
