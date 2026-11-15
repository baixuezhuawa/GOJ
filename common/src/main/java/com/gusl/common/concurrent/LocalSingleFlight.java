package com.gusl.common.concurrent;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * JVM 内按业务键合并并发加载请求的工具类。
 *
 * <p>同一个 key 在同一时刻只允许一个请求执行 supplier，其他请求等待同一个结果。
 * supplier 抛出异常时，异常会传递给当前批次的所有等待者；本次执行结束后会移除
 * in-flight 记录，下一次请求可以重新加载。</p>
 *
 * @param <K> 合并请求使用的业务键类型
 * @param <V> 加载结果类型
 */
public final class LocalSingleFlight<K, V> {

    /** 当前正在加载的请求；完成后立即删除，不承担缓存职责。 */
    private final ConcurrentHashMap<K, CompletableFuture<V>> inFlight = new ConcurrentHashMap<>();

    /**
     * 执行一次按 key 合并的加载操作。
     *
     * @param key 业务键
     * @param supplier 缓存未命中时执行的加载逻辑
     * @return 加载结果
     * @throws NullPointerException key 或 supplier 为空
     * @throws java.util.concurrent.CompletionException 加载逻辑抛出异常时包装原始异常
     */
    public V execute(K key, Supplier<V> supplier) {
        Objects.requireNonNull(key, "single-flight key 不能为空");
        Objects.requireNonNull(supplier, "single-flight supplier 不能为空");

        // 创建一个future
        CompletableFuture<V> created = new CompletableFuture<>();
        // 获取当前Single-flight执行权的互斥锁, 如果不存在, 则
        CompletableFuture<V> existing = inFlight.putIfAbsent(key, created);
        if (existing != null) {
            return await(existing);
        }

        try {
            // 执行
            created.complete(supplier.get());
        } catch (Throwable throwable) {
            // 先广播失败结果，再由统一等待逻辑转换异常，保证首个请求与等待请求行为一致。
            created.completeExceptionally(throwable);
        } finally {
            // 只删除本次 Future，避免极端情况下误删后续已经创建的新请求。
            inFlight.remove(key, created);
        }

        return await(created);
    }

    /**
     * 等待当前批次结果，并统一传播异步异常。
     */
    private V await(CompletableFuture<V> future) {
        return future.join();
    }
}
