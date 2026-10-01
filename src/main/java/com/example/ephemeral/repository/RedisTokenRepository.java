package com.example.ephemeral.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Redis access for single-use playback tokens.
 * Key layout: {@code video:token:{videoId}} -> token value, always with an explicit TTL.
 */
@Repository
@RequiredArgsConstructor
public class RedisTokenRepository {

    private static final String TOKEN_KEY_PREFIX = "video:token:";
    private static final String LOCK_KEY_PREFIX = "lock:";

    /** Compare-and-delete executed atomically inside Redis (single round trip, no race). */
    private static final DefaultRedisScript<Long> BURN_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;

    /** SET NX EX: succeeds only if no live token exists for the video. */
    public boolean issueIfAbsent(UUID videoId, String token, Duration ttl) {
        Boolean created = redis.opsForValue().setIfAbsent(tokenKey(videoId), token, ttl);
        return Boolean.TRUE.equals(created);
    }

    /** SET EX: replaces any existing token for the video. */
    public void issue(UUID videoId, String token, Duration ttl) {
        redis.opsForValue().set(tokenKey(videoId), token, ttl);
    }

    /** Atomically deletes the token iff it equals the supplied value. True means "burned by this call". */
    public boolean burnIfMatches(UUID videoId, String token) {
        Long result = redis.execute(BURN_SCRIPT, List.of(tokenKey(videoId)), token);
        return result != null && result == 1L;
    }

    public void delete(UUID videoId) {
        redis.delete(tokenKey(videoId));
    }

    /** Best-effort distributed lock so only one node runs the sweeper per interval. */
    public boolean tryAcquireLock(String name, Duration ttl) {
        Boolean acquired = redis.opsForValue().setIfAbsent(LOCK_KEY_PREFIX + name, "1", ttl);
        return Boolean.TRUE.equals(acquired);
    }

    private String tokenKey(UUID videoId) {
        return TOKEN_KEY_PREFIX + videoId;
    }
}
