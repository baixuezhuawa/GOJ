package com.gusl.gojserver.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.filter.Filter;
import com.gusl.common.constant.CommonConstants;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Fastjson2 serializer for values stored in Redis.
 */
public final class FastJson2JsonRedisSerializer<T> implements RedisSerializer<T> {

    private static final Filter AUTO_TYPE_FILTER = JSONReader.autoTypeFilter("com.gusl.gojserver.");

    private final Class<T> type;

    public FastJson2JsonRedisSerializer(Class<T> type) {
        this.type = Objects.requireNonNull(type, "type " + CommonConstants.REQUIRE_NOT_NULL);
    }

    @Override
    public byte[] serialize(T value) throws SerializationException {
        if (value == null) {
            return new byte[0];
        }

        try {
            return JSON.toJSONString(value, JSONWriter.Feature.WriteClassName)
                    .getBytes(StandardCharsets.UTF_8);
        } catch (RuntimeException exception) {
            throw new SerializationException(CommonConstants.NOT_SERIALIZE_TO_REDIS_VALUE_EXCEPTION, exception);
        }
    }

    @Override
    public T deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }

        try {
            String json = new String(bytes, StandardCharsets.UTF_8);
            return JSON.parseObject(json, type, AUTO_TYPE_FILTER);
        } catch (RuntimeException exception) {
            throw new SerializationException(CommonConstants.NOT_SERIALIZE_TO_REDIS_VALUE_EXCEPTION, exception);
        }
    }
}
