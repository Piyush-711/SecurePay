package com.securepay.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitingService {

    private final Map<String, Bucket> bucketCache = new ConcurrentHashMap<>();
    private final int defaultLimitPerMinute;

    public RateLimitingService(@Value("${securepay.rate-limiting.default-requests-per-minute:60}") int defaultLimitPerMinute) {
        this.defaultLimitPerMinute = defaultLimitPerMinute;
    }

    public boolean tryConsume(String identifier, int capacityPerMinute) {
        Bucket bucket = bucketCache.computeIfAbsent(identifier, k -> createNewBucket(capacityPerMinute));
        return bucket.tryConsume(1);
    }

    public boolean tryConsume(String identifier) {
        return tryConsume(identifier, defaultLimitPerMinute);
    }

    public long getAvailableTokens(String identifier, int capacityPerMinute) {
        Bucket bucket = bucketCache.computeIfAbsent(identifier, k -> createNewBucket(capacityPerMinute));
        return bucket.getAvailableTokens();
    }

    private Bucket createNewBucket(int capacityPerMinute) {
        int capacity = capacityPerMinute > 0 ? capacityPerMinute : defaultLimitPerMinute;
        Refill refill = Refill.intervally(capacity, Duration.ofMinutes(1));
        Bandwidth limit = Bandwidth.classic(capacity, refill);
        return Bucket.builder().addLimit(limit).build();
    }

    public void clear() {
        bucketCache.clear();
    }
}
