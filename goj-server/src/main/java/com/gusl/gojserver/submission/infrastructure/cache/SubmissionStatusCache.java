package com.gusl.gojserver.submission.infrastructure.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gusl.common.constant.JudgingConstant;
import com.gusl.gojserver.shared.infrastructure.config.properties.SubmissionCacheProperties;
import com.gusl.gojserver.submission.application.model.SubmissionStatusSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

/** 提交状态缓存，完整替换结果并保留相同结果原有的过期时间。 */
@Component
@RequiredArgsConstructor
public class SubmissionStatusCache {
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final SubmissionCacheProperties properties;

    // Lua 在 Redis 内原子完成替换与过期设置，重复事件不会延长同一结果的期限。
    private static final DefaultRedisScript<Long> WRITE = new DefaultRedisScript<>(
            "if ARGV[3] == 'true' and redis.call('exists', KEYS[1]) == 1 then " +
            "redis.call('set', KEYS[1], ARGV[1], 'KEEPTTL') " +
            "else redis.call('set', KEYS[1], ARGV[1], 'PX', ARGV[2]) end return 1", Long.class);

    /** 读取快照，损坏数据按未命中处理，避免消费反复卡在旧缓存。 */
    public SubmissionStatusSnapshot get(Long id) {
        String value = redis.opsForValue().get(key(id));
        if (value == null) return null;
        try {
            return objectMapper.readValue(value, SubmissionStatusSnapshot.class);
        } catch (JsonProcessingException exception) {
            redis.delete(key(id));
            return null;
        }
    }

    /** 写入完整快照；null 字段也会覆盖旧结果。 */
    public void put(SubmissionStatusSnapshot snapshot, SubmissionStatusSnapshot previous) {
        long seconds = properties.getActiveTtlSeconds();
        if (JudgingConstant.TERMINAL_STATUSES.contains(snapshot.getStatus())) {
            seconds = properties.getTerminalTtlSeconds() +
                    ThreadLocalRandom.current().nextLong(properties.getTerminalJitterSeconds() + 1);
        }
        try {
            redis.execute(WRITE, Collections.singletonList(key(snapshot.getId())),
                    objectMapper.writeValueAsString(snapshot), String.valueOf(seconds * 1000),
                    String.valueOf(snapshot.equals(previous)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("提交缓存内容无法编码", exception);
        }
    }

    /** 删除不存在或不在本功能范围内的提交缓存。 */
    public void delete(Long id) {
        redis.delete(key(id));
    }

    /** 生成统一提交结果键。 */
    private String key(Long id) {
        return "goj:submission:result:" + id;
    }
}
