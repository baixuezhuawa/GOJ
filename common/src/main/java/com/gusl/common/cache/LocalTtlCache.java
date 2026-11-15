package com.gusl.common.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JVM 内的轻量 TTL 缓存。
 *
 * <p>该类只负责本地值的过期判断，不负责 Redis 同步或跨实例一致性。</p>
 *
 * @param <K> 缓存键类型
 * @param <V> 缓存值类型
 */
public final class LocalTtlCache<K, V> {

    /** 默认最多保留的本地缓存条目数，避免 L1 无界增长。 */
    private static final int DEFAULT_MAX_ENTRIES = 256;

    private final ConcurrentHashMap<K, Entry<V>> entries = new ConcurrentHashMap<>();

    private final int maxEntries;

    /** 创建使用默认容量上限的本地缓存。 */
    public LocalTtlCache() {
        this(DEFAULT_MAX_ENTRIES);
    }

    /**
     * 创建本地缓存。
     *
     * @param maxEntries 最大缓存条目数
     */
    public LocalTtlCache(int maxEntries) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("本地缓存容量必须大于 0");
        }
        this.maxEntries = maxEntries;
    }

    /**
     * 写入本地缓存。
     *
     * @param key 缓存键
     * @param value 缓存值
     * @param freshTtl 新鲜值有效时间
     */
    public void put(K key, V value, Duration freshTtl) {
        Objects.requireNonNull(key, "缓存键不能为空");
        Objects.requireNonNull(value, "缓存值不能为空");
        Objects.requireNonNull(freshTtl, "新鲜值 TTL 不能为空");
        if (freshTtl.isNegative()) {
            throw new IllegalArgumentException("缓存 TTL 不能为负数");
        }

        Instant now = Instant.now();
        entries.put(key, new Entry<>(
                value,
                now.plus(freshTtl)
        ));
        evictIfNecessary();
    }

    /**
     * 读取新鲜值；新鲜时间已过期时返回空值。
     */
    public synchronized V get(K key) {
        Entry<V> entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        if (Instant.now().isBefore(entry.freshUntil())) {
            return entry.value();
        }
        entries.remove(key, entry);
        return null;
    }

    /**
     * 删除指定缓存键。
     */
    public void invalidate(K key) {
        entries.remove(key);
    }

    /**如果大于限制的数量则删除 **/
    private void evictIfNecessary() {
        while (entries.size() > maxEntries) {
            // 本地缓存只承担 L1 加速和故障降级，简单删除一个条目即可，不引入全局淘汰锁。
            K firstKey = entries.keySet().stream().findFirst().orElse(null);
            if (firstKey == null) {
                return;
            }
            entries.remove(firstKey);
        }
    }

    /** Java 16 引入的 record 只承载不可变缓存条目，避免为内部值对象编写样板 getter。**/
    private record Entry<V>(V value, Instant freshUntil) { }
}
