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
    
    private RateLimiter authRateLimiter = new RateLimiter(5, 60000); // 5 attempts per 60s per IP

    public static class LoginRequest {
        public final String code;
        public final String privateToken;
        public final LocalDateTime expiresAt;
        
        public UUID playerUuid;
        public String username;
        public String status; // "waiting", "player_found", "confirmed", "cancelled"

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

    public synchronized void reconfigureRateLimiter(int maxAttempts) {
        this.authRateLimiter = new RateLimiter(maxAttempts, 60000);
    }

    public synchronized LoginRequest createLoginRequest(int expiresInSeconds) {
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
            requestsByToken.remove(privateToken);
            requestsByCode.remove(req.code);
            return null;
        }
        return req;
    }

    public LoginRequest getRequestByCode(String code) {
        if (code == null) return null;
        LoginRequest req = requestsByCode.get(code);
        if (req != null && req.isExpired()) {
            requestsByToken.remove(req.privateToken);
            requestsByCode.remove(code);
            return null;
        }
        return req;
    }

    public synchronized void consumeCode(String code) {
        requestsByCode.remove(code);
    }

    public void removeRequest(String privateToken) {
        LoginRequest req = requestsByToken.remove(privateToken);
        if (req != null) {
            requestsByCode.remove(req.code);
        }
    }
}
