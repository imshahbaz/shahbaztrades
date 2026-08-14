package com.app.shahbaztrades.config.redis;

import org.apache.fory.ThreadSafeFory;
import org.apache.fory.config.CompatibleMode;
import org.apache.fory.config.ForyBuilder;
import org.apache.fory.config.Language;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.stereotype.Component;

@Component
public class ForyRedisSerializer<T> implements RedisSerializer<T> {

    private final ThreadSafeFory fory;

    public ForyRedisSerializer() {
        this.fory = new ForyBuilder()
                .withLanguage(Language.JAVA)
                .withRefTracking(false)
                .requireClassRegistration(false)
                .withCompatibleMode(CompatibleMode.COMPATIBLE)
                .withAsyncCompilation(true)
                .withDeserializeUnknownClass(true)
                .serializeEnumByName(true)
                .buildThreadSafeForyPool(20);
    }

    @Override
    public byte @NonNull [] serialize(@Nullable T value) throws SerializationException {
        if (value == null) return new byte[0];
        try {
            return fory.serialize(value);
        } catch (Exception e) {
            throw new SerializationException("Error serializing object with fory", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nullable T deserialize(byte @Nullable [] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) return null;
        try {
            return (T) fory.deserialize(bytes);
        } catch (Exception e) {
            throw new SerializationException("Error deserializing object with fory", e);
        }
    }

}
