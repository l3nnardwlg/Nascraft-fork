package me.bounser.nascraft.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RateLimiter {
    private final Map<String, List<Long>> ipAttempts = new HashMap<>();
    private final int maxAttempts;
    private final long periodMs;

    public RateLimiter(int maxAttempts, long periodMs) {
        this.maxAttempts = maxAttempts;
        this.periodMs = periodMs;
    }

    public synchronized boolean isRateLimited(String ip) {
        if (ip == null) return false;
        long now = System.currentTimeMillis();
        List<Long> attempts = ipAttempts.computeIfAbsent(ip, k -> new ArrayList<>());
        attempts.removeIf(timestamp -> now - timestamp > periodMs);
        if (attempts.size() >= maxAttempts) {
            return true;
        }
        attempts.add(now);
        return false;
    }
}
