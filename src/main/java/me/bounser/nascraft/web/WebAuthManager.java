package me.bounser.nascraft.web;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WebAuthManager {

    private static final WebAuthManager instance = new WebAuthManager();
    public static WebAuthManager getInstance() { return instance; }

    private final Map<String, LoginRequest> requestsByToken = new ConcurrentHashMap<>();
    private final Map<String, LoginRequest> requestsByCode = new ConcurrentHashMap<>();

    private RateLimiter authRateLimiter = new RateLimiter(5, 60_000); // browser/IP requests
    private RateLimiter playerCodeRateLimiter = new RateLimiter(5, 60_000); // ingame code attempts

    public static class LoginRequest {
        public final String code;
        public final String privateToken;
        public final LocalDateTime expiresAt;

        public UUID playerUuid;
        public String username;
        public String status; // waiting, player_found, confirmed, cancelled

        public LoginRequest(String code, String privateToken, int expiresInSeconds) {
            this.code = code;
            this.privateToken = privateToken;
            this.expiresAt = LocalDateTime.now().plusSeconds(expiresInSeconds);
            this.status = "waiting";
        }

        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expiresAt);
        }
    }

    public RateLimiter getRateLimiter() {
        return authRateLimiter;
    }

    public boolean isPlayerCodeRateLimited(UUID playerUuid) {
        return playerUuid != null && playerCodeRateLimiter.isRateLimited(playerUuid.toString());
    }

    public synchronized void reconfigureRateLimiter(int maxAttempts) {
        int safeMax = Math.max(1, maxAttempts);
        this.authRateLimiter = new RateLimiter(safeMax, 60_000);
        this.playerCodeRateLimiter = new RateLimiter(safeMax, 60_000);
    }

    public synchronized LoginRequest createLoginRequest(int expiresInSeconds) {
        purgeExpiredRequests();

        String code = SecurityUtils.generate6DigitCode();
        while (requestsByCode.containsKey(code)) {
            code = SecurityUtils.generate6DigitCode();
        }

        String privateToken = SecurityUtils.generateSecureToken();
        LoginRequest req = new LoginRequest(code, privateToken, expiresInSeconds);
        requestsByToken.put(privateToken, req);
        requestsByCode.put(code, req);
        return req;
    }

    public LoginRequest getRequestByToken(String privateToken) {
        if (privateToken == null) return null;
        LoginRequest req = requestsByToken.get(privateToken);
        if (req != null && req.isExpired()) {
            removeRequest(privateToken);
            return null;
        }
        return req;
    }

    public LoginRequest getRequestByCode(String code) {
        if (code == null) return null;
        LoginRequest req = requestsByCode.get(code);
        if (req != null && req.isExpired()) {
            removeRequest(req.privateToken);
            return null;
        }
        return req;
    }

    public synchronized void consumeCode(String code) {
        requestsByCode.remove(code);
    }

    public synchronized void removeRequest(String privateToken) {
        LoginRequest req = requestsByToken.remove(privateToken);
        if (req != null) {
            requestsByCode.remove(req.code);
        }
    }

    private synchronized void purgeExpiredRequests() {
        requestsByToken.entrySet().removeIf(entry -> {
            LoginRequest req = entry.getValue();
            if (!req.isExpired()) return false;
            requestsByCode.remove(req.code);
            return true;
        });
    }
}
