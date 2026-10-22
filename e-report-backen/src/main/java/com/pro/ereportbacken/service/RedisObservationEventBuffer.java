package com.pro.ereportbacken.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pro.ereportbacken.model.dto.BufferedObservationBatch;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@Log4j2
public class RedisObservationEventBuffer implements ObservationEventBuffer {

    private static final String KEY_PREFIX = "e-report:observability:v1:";
    private static final String READY_KEY = KEY_PREFIX + "ready";
    private static final String PROCESSING_KEY = KEY_PREFIX + "processing";
    private static final String DEAD_LETTER_KEY = KEY_PREFIX + "dead-letter";
    private static final String ATTEMPTS_KEY = KEY_PREFIX + "attempts";
    private static final String BUFFERED_BYTES_KEY = KEY_PREFIX + "buffered-bytes";
    private static final long MAX_READY_BATCHES = 1_000;
    private static final long MAX_BUFFERED_BYTES = 64L * 1024 * 1024;
    private static final int MAX_SERIALIZED_BATCH_BYTES = 512 * 1024;
    private static final long MAX_DEAD_LETTER_BATCHES = 32;
    private static final int MAX_ATTEMPTS = 5;
    private static final DefaultRedisScript<Long> ENQUEUE_SCRIPT = new DefaultRedisScript<>("""
            local size = redis.call('LLEN', KEYS[1])
            local bufferedBytes = tonumber(redis.call('GET', KEYS[2]) or '0')
            local payloadBytes = string.len(ARGV[3])
            if size >= tonumber(ARGV[1]) or bufferedBytes + payloadBytes > tonumber(ARGV[2]) then
              return -1
            end
            redis.call('LPUSH', KEYS[1], ARGV[3])
            redis.call('INCRBY', KEYS[2], payloadBytes)
            return size + 1
            """, Long.class);
    private static final DefaultRedisScript<Long> ACKNOWLEDGE_SCRIPT = new DefaultRedisScript<>("""
            local removed = redis.call('LREM', KEYS[1], 1, ARGV[1])
            if removed > 0 then
              local remainingBytes = redis.call('DECRBY', KEYS[2], string.len(ARGV[1]))
              if remainingBytes <= 0 then
                redis.call('DEL', KEYS[2])
              end
            end
            redis.call('HDEL', KEYS[3], ARGV[2])
            return removed
            """, Long.class);
    private static final DefaultRedisScript<Long> DEAD_LETTER_SCRIPT = new DefaultRedisScript<>("""
            local removed = redis.call('LREM', KEYS[1], 1, ARGV[1])
            if removed > 0 then
              local remainingBytes = redis.call('DECRBY', KEYS[2], string.len(ARGV[1]))
              if remainingBytes <= 0 then
                redis.call('DEL', KEYS[2])
              end
              redis.call('LPUSH', KEYS[3], ARGV[1])
              redis.call('LTRIM', KEYS[3], 0, tonumber(ARGV[2]) - 1)
            end
            if ARGV[3] ~= '' then
              redis.call('HDEL', KEYS[4], ARGV[3])
            end
            return removed
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisObservationEventBuffer(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void enqueue(BufferedObservationBatch batch) {
        try {
            String serialized = objectMapper.writeValueAsString(batch);
            if (serialized.getBytes(StandardCharsets.UTF_8).length > MAX_SERIALIZED_BATCH_BYTES) {
                throw new ResponseStatusException(
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        "运行诊断单批数据不能超过512KB。"
                );
            }
            Long queueSize = redisTemplate.execute(
                    ENQUEUE_SCRIPT,
                    List.of(READY_KEY, BUFFERED_BYTES_KEY),
                    String.valueOf(MAX_READY_BATCHES),
                    String.valueOf(MAX_BUFFERED_BYTES),
                    serialized
            );
            if (queueSize == null || queueSize < 0) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "运行诊断缓冲队列已满，请稍后重试。"
                );
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("运行诊断事件序列化失败。", exception);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "运行诊断缓冲服务暂时不可用，请稍后重试。",
                    exception
            );
        }
    }

    @Override
    public List<ClaimedBatch> claim(int maximumBatchCount) {
        int limit = Math.max(1, maximumBatchCount);
        Set<String> serializedBatches = new LinkedHashSet<>();
        try {
            List<String> pending = redisTemplate.opsForList().range(PROCESSING_KEY, 0, limit - 1L);
            if (pending != null) {
                serializedBatches.addAll(pending);
            }
            while (serializedBatches.size() < limit) {
                String value = redisTemplate.opsForList().rightPopAndLeftPush(READY_KEY, PROCESSING_KEY);
                if (value == null) {
                    break;
                }
                serializedBatches.add(value);
            }
        } catch (DataAccessException exception) {
            log.warn("运行诊断消费者无法连接 Redis，将在下一轮重试。", exception);
            return List.of();
        }

        List<ClaimedBatch> claimed = new ArrayList<>();
        for (String serialized : serializedBatches) {
            try {
                BufferedObservationBatch batch = objectMapper.readValue(serialized, BufferedObservationBatch.class);
                if (batch.schemaVersion() != BufferedObservationBatch.CURRENT_SCHEMA_VERSION) {
                    moveToDeadLetter(serialized, batch.messageId(), "不支持的队列消息版本");
                    continue;
                }
                claimed.add(new ClaimedBatch(serialized, batch));
            } catch (Exception exception) {
                moveToDeadLetter(serialized, null, "队列消息无法反序列化");
            }
        }
        return List.copyOf(claimed);
    }

    @Override
    public void acknowledge(ClaimedBatch batch) {
        redisTemplate.execute(
                ACKNOWLEDGE_SCRIPT,
                List.of(PROCESSING_KEY, BUFFERED_BYTES_KEY, ATTEMPTS_KEY),
                batch.serializedValue(),
                batch.batch().messageId()
        );
    }

    @Override
    public boolean recordFailure(ClaimedBatch batch) {
        Long attempts = redisTemplate.opsForHash().increment(ATTEMPTS_KEY, batch.batch().messageId(), 1);
        if (attempts != null && attempts >= MAX_ATTEMPTS) {
            moveToDeadLetter(batch.serializedValue(), batch.batch().messageId(), "数据库批量写入连续失败");
            return true;
        }
        return false;
    }

    private void moveToDeadLetter(String serializedValue, String messageId, String reason) {
        redisTemplate.execute(
                DEAD_LETTER_SCRIPT,
                List.of(PROCESSING_KEY, BUFFERED_BYTES_KEY, DEAD_LETTER_KEY, ATTEMPTS_KEY),
                serializedValue,
                String.valueOf(MAX_DEAD_LETTER_BATCHES),
                messageId == null ? "" : messageId
        );
        log.error("运行诊断队列消息已进入死信队列。messageId={}, reason={}", messageId, reason);
    }
}
