package me.bounser.nascraft.web;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;
import me.bounser.nascraft.Services;
import me.bounser.nascraft.chart.cpi.CPIInstant;
import me.bounser.nascraft.database.Database;
import me.bounser.nascraft.database.DatabaseManager;
import me.bounser.nascraft.market.resources.Category;
import me.bounser.nascraft.market.unit.Item;
import me.bounser.nascraft.market.unit.stats.Instant;
import me.bounser.nascraft.portfolio.Portfolio;
import me.bounser.nascraft.web.dto.CategoryDTO;
import me.bounser.nascraft.web.dto.ItemDTO;
import me.bounser.nascraft.web.dto.PortfolioDTO;
import me.bounser.nascraft.web.dto.TimeSeriesDTO;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import me.bounser.nascraft.web.WebAuthManager;
import me.bounser.nascraft.web.SecurityUtils;
import me.bounser.nascraft.portfolio.PortfolioService;
import me.bounser.nascraft.market.TradeService;
import me.bounser.nascraft.managers.MoneyManager;
import me.bounser.nascraft.managers.currencies.CurrenciesManager;
import me.bounser.nascraft.managers.currencies.Currency;
import me.bounser.nascraft.database.commands.resources.Trade;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Self-hosted market website restored from the Nascraft 1.9.1 release.
 *
 * The public source repository did not contain this implementation even though
 * it was present in the published 1.9.1 JAR. The API surface intentionally
 * remains compatible with that release.
 */
public final class WebServerManager {

    private final JavaPlugin plugin;
    private final int port;
    private final String externalWebRootPath;
    private final WebConfig webConfig;
    private Javalin webServer;

    public WebServerManager(JavaPlugin plugin, WebConfig webConfig) {
        this.plugin = plugin;
        this.webConfig = webConfig;
        this.port = webConfig.port();
        this.externalWebRootPath = new File(plugin.getDataFolder(), "web").getAbsolutePath();
        WebAuthManager.getInstance().reconfigureRateLimiter(webConfig.maxCodeAttemptsPerMinute());
    }

    public synchronized void startServer() {
        if (webServer != null) {
            plugin.getLogger().warning("Web server is already running!");
            return;
        }

        File webRoot = new File(externalWebRootPath);
        if (!webRoot.isDirectory()) {
            plugin.getLogger().severe("-------------------------------------------------------");
            plugin.getLogger().severe("External web directory not found or is not a directory!");
            plugin.getLogger().severe("Expected: " + externalWebRootPath);
            plugin.getLogger().severe("Web server cannot start.");
            plugin.getLogger().severe("-------------------------------------------------------");
            return;
        }

        try {
            Javalin app = Javalin.create(config -> {
                config.showJavalinBanner = false;
                config.staticFiles.add(files -> {
                    files.hostedPath = "/";
                    files.directory = externalWebRootPath;
                    files.location = Location.EXTERNAL;
                });
            });

            registerRoutes(app);
            webServer = app.start(port);
            plugin.getLogger().info("Web server started successfully on port " + port + ".");
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to start web server on port " + port + ": " + exception.getMessage(), exception);
            webServer = null;
        }
    }

    private void registerRoutes(Javalin app) {
        app.get("/api/items", ctx -> ctx.json(getAllItemData()));
        app.get("/api/top-portfolios", ctx -> ctx.json(getTopPortfolios()));
        app.get("/api/categories", ctx -> ctx.json(getCategories()));
        app.get("/api/charts/cpi", ctx -> ctx.json(getCpiTimeSeries()));
        app.get("/api/config", ctx -> ctx.json(getPublicConfig()));

        app.get("/api/popular-item", ctx -> {
            ItemDTO popular = getPopularItem();
            if (popular == null) ctx.status(404).result("Popular item data not available.");
            else ctx.json(popular);
        });

        app.get("/api/charts/item/{identifier}", ctx -> {
            List<TimeSeriesDTO> data = getItemTimeSeries(ctx.pathParam("identifier"));
            if (data == null) ctx.status(404).result("Unknown market item.");
            else ctx.json(data);
        });

        app.get("/api/icons/{identifier}.png", this::serveIcon);

        // AUTH & ACCOUNT LINKING (Nascraft 1.9.5)
        app.post("/api/auth/request", ctx -> {
            if (WebAuthManager.getInstance().getRateLimiter().isRateLimited(ctx.ip())) {
                ctx.status(429).result("RATE_LIMIT_EXCEEDED");
                return;
            }
            WebAuthManager.LoginRequest req = WebAuthManager.getInstance().createLoginRequest(webConfig.codeExpirationSeconds());
            plugin.getLogger().info("[Nascraft Web] Login code requested.");
            
            ctx.cookie(new io.javalin.http.Cookie("nascraft_login_token", req.privateToken, "/", webConfig.codeExpirationSeconds(), ctx.scheme().equals("https"), 0, true, null, null, io.javalin.http.SameSite.STRICT));
            
            Map<String, Object> resp = new HashMap<>();
            resp.put("code", req.code);
            resp.put("expiresIn", webConfig.codeExpirationSeconds());
            ctx.json(resp);
        });

        app.get("/api/auth/status", ctx -> {
            String privateToken = ctx.cookie("nascraft_login_token");
            if (privateToken == null) {
                ctx.json(Map.of("status", "expired"));
                return;
            }
            WebAuthManager.LoginRequest req = WebAuthManager.getInstance().getRequestByToken(privateToken);
            if (req == null || req.isExpired()) {
                ctx.json(Map.of("status", "expired"));
            } else {
                Map<String, Object> resp = new HashMap<>();
                resp.put("status", req.status);
                if (req.username != null) {
                    resp.put("username", req.username);
                }
                ctx.json(resp);
            }
        });

        app.post("/api/auth/confirm", ctx -> {
            String privateToken = ctx.cookie("nascraft_login_token");
            WebAuthManager.LoginRequest req = WebAuthManager.getInstance().getRequestByToken(privateToken);
            if (req == null || !"player_found".equals(req.status)) {
                ctx.status(400).result("LOGIN_CODE_INVALID");
                return;
            }
            
            req.status = "confirmed";
            String sessionToken = SecurityUtils.generateSecureToken();
            String sessionHash = SecurityUtils.hashToken(sessionToken);
            
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expiresAt = now.plusDays(webConfig.sessionExpirationDays());
            
            DatabaseManager.get().getDatabase().saveWebSession(sessionHash, req.playerUuid, now, now, expiresAt);
            plugin.getLogger().info("[Nascraft Web] Web session confirmed for " + req.username + ".");
            
            ctx.cookie(new io.javalin.http.Cookie("nascraft_session", sessionToken, "/", (int) java.util.concurrent.TimeUnit.DAYS.toSeconds(webConfig.sessionExpirationDays()), ctx.scheme().equals("https"), 0, true, null, null, io.javalin.http.SameSite.STRICT));
            ctx.cookie(new io.javalin.http.Cookie("nascraft_login_token", "", "/", 0, ctx.scheme().equals("https"), 0, true, null, null, io.javalin.http.SameSite.STRICT));
            WebAuthManager.getInstance().removeRequest(privateToken);
            
            ctx.json(Map.of("uuid", req.playerUuid.toString(), "username", req.username));
        });

        app.post("/api/auth/logout", ctx -> {
            String sessionToken = ctx.cookie("nascraft_session");
            if (sessionToken != null) {
                DatabaseManager.get().getDatabase().deleteWebSession(SecurityUtils.hashToken(sessionToken));
            }
            ctx.cookie(new io.javalin.http.Cookie("nascraft_session", "", "/", 0, ctx.scheme().equals("https"), 0, true, null, null, io.javalin.http.SameSite.STRICT));
            ctx.json(Map.of("success", true));
        });

        app.get("/api/me", ctx -> {
            UUID playerUuid = getAuthenticatedPlayerUUID(ctx);
            if (playerUuid == null) {
                ctx.status(401).result("NOT_AUTHENTICATED");
                return;
            }
            String username = DatabaseManager.get().getDatabase().getNameByUUID(playerUuid);
            if (username == null || username.isBlank()) {
                username = Bukkit.getOfflinePlayer(playerUuid).getName();
            }
            if (username == null) username = "Unknown Player";
            
            ctx.json(Map.of("uuid", playerUuid.toString(), "username", username));
        });

        // PORTFOLIO & TRADING (Nascraft 1.9.5)
        app.get("/api/me/portfolio", ctx -> {
            UUID playerUuid = getAuthenticatedPlayerUUID(ctx);
            if (playerUuid == null) {
                ctx.status(401).result("NOT_AUTHENTICATED");
                return;
            }
            ctx.json(getPortfolioData(playerUuid));
        });

        app.post("/api/me/portfolio/unlock-slot", ctx -> {
            UUID playerUuid = getAuthenticatedPlayerUUID(ctx);
            if (playerUuid == null) {
                ctx.status(401).result("NOT_AUTHENTICATED");
                return;
            }
            Portfolio portfolio = me.bounser.nascraft.portfolio.PortfoliosManager.getInstance().getPortfolio(playerUuid);
            if (portfolio.getCapacity() >= 27) {
                ctx.status(400).result("NO_MORE_PORTFOLIO_SLOTS");
                return;
            }
            double price = portfolio.getNextSlotPrice();
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUuid);
            Currency currency = CurrenciesManager.getInstance().getDefaultCurrency();
            
            if (!MoneyManager.getInstance().hasEnoughMoney(offlinePlayer, currency, price)) {
                ctx.status(400).result("NOT_ENOUGH_MONEY");
                return;
            }
            
            boolean success = PortfolioService.unlockNextSlot(playerUuid);
            if (success) {
                String username = DatabaseManager.get().getDatabase().getNameByUUID(playerUuid);
                if (username == null || username.isBlank()) username = offlinePlayer.getName();
                plugin.getLogger().info("[Nascraft Web] " + username + " unlocked portfolio slot " + portfolio.getCapacity() + " via web.");
                ctx.json(getPortfolioData(playerUuid));
            } else {
                ctx.status(400).result("TRADE_FAILED");
            }
        });

        app.post("/api/trade/buy", ctx -> {
            UUID playerUuid = getAuthenticatedPlayerUUID(ctx);
            if (playerUuid == null) {
                ctx.status(401).result("NOT_AUTHENTICATED");
                return;
            }
            
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            String itemIdentifier = (String) body.get("item");
            int amount = ((Number) body.get("amount")).intValue();
            
            Item item = Services.get().market().getItem(itemIdentifier);
            if (item == null) {
                ctx.status(404).result("ITEM_NOT_FOUND");
                return;
            }
            
            TradeService.TradeResult result = TradeService.buyToPortfolio(playerUuid, item, amount).get();
            if (result.success) {
                String username = DatabaseManager.get().getDatabase().getNameByUUID(playerUuid);
                if (username == null || username.isBlank()) username = Bukkit.getOfflinePlayer(playerUuid).getName();
                plugin.getLogger().info("[Nascraft Web] " + username + " bought " + amount + " " + item.getIdentifier() + " via web.");
                ctx.json(Map.of("success", true, "worth", result.worth));
            } else {
                ctx.status(400).result(result.error.name());
            }
        });

        app.post("/api/trade/sell", ctx -> {
            UUID playerUuid = getAuthenticatedPlayerUUID(ctx);
            if (playerUuid == null) {
                ctx.status(401).result("NOT_AUTHENTICATED");
                return;
            }
            
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            String itemIdentifier = (String) body.get("item");
            int amount = ((Number) body.get("amount")).intValue();
            
            Item item = Services.get().market().getItem(itemIdentifier);
            if (item == null) {
                ctx.status(404).result("ITEM_NOT_FOUND");
                return;
            }
            
            TradeService.TradeResult result = TradeService.sellFromPortfolio(playerUuid, item, amount).get();
            if (result.success) {
                String username = DatabaseManager.get().getDatabase().getNameByUUID(playerUuid);
                if (username == null || username.isBlank()) username = Bukkit.getOfflinePlayer(playerUuid).getName();
                plugin.getLogger().info("[Nascraft Web] " + username + " sold " + amount + " " + item.getIdentifier() + " via web.");
                ctx.json(Map.of("success", true, "worth", result.worth));
            } else {
                ctx.status(400).result(result.error.name());
            }
        });

        app.get("/api/me/trades", ctx -> {
            UUID playerUuid = getAuthenticatedPlayerUUID(ctx);
            if (playerUuid == null) {
                ctx.status(401).result("NOT_AUTHENTICATED");
                return;
            }
            
            List<Trade> trades = DatabaseManager.get().getDatabase().retrieveTrades(playerUuid, 0, 50);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Trade t : trades) {
                Map<String, Object> m = new HashMap<>();
                m.put("item", t.getItem().getIdentifier());
                m.put("displayName", t.getItem().getName());
                m.put("amount", t.getAmount());
                m.put("value", t.getValue());
                m.put("buy", t.isBuy());
                m.put("date", t.getDate().toString());
                result.add(m);
            }
            ctx.json(result);
        });
    }

    private List<ItemDTO> getAllItemData() {
        List<ItemDTO> result = new ArrayList<>();
        for (Item item : Services.get().market().getAllParentItems()) result.add(toItemDto(item));
        return result;
    }

    private ItemDTO getPopularItem() {
        List<Item> items = Services.get().market().getMostTraded(1);
        return items.isEmpty() ? null : toItemDto(items.getFirst());
    }

    private ItemDTO toItemDto(Item item) {
        double change = Math.round(item.getPrice().getValueChangeLastHour() * 10.0d) / 10.0d;
        return new ItemDTO(
                item.getIdentifier(),
                item.getName(),
                item.getPrice().getValue(),
                item.getPrice().getBuyPrice(),
                item.getPrice().getSellPrice(),
                item.getOperations(),
                change
        );
    }

    private List<TimeSeriesDTO> getCpiTimeSeries() {
        List<TimeSeriesDTO> result = new ArrayList<>();
        List<CPIInstant> history = DatabaseManager.get().getDatabase().getCPIHistory();
        if (history == null) return result;

        for (CPIInstant instant : history) {
            if (instant == null || instant.getLocalDateTime() == null) continue;
            result.add(new TimeSeriesDTO(
                    instant.getLocalDateTime().toEpochSecond(ZoneOffset.UTC),
                    instant.getIndexValue()
            ));
        }
        return result;
    }

    private List<TimeSeriesDTO> getItemTimeSeries(String identifier) {
        Item item = Services.get().market().getItem(identifier);
        if (item == null) return null;

        List<TimeSeriesDTO> result = new ArrayList<>();
        Set<Long> timestamps = new HashSet<>();
        List<Instant> prices = DatabaseManager.get().getDatabase().getAllPrices(item);
        if (prices == null) return result;

        for (Instant instant : prices) {
            if (instant == null || instant.getLocalDateTime() == null || instant.getPrice() == 0) continue;
            long time = instant.getLocalDateTime().toEpochSecond(ZoneOffset.UTC);
            if (timestamps.add(time)) result.add(new TimeSeriesDTO(time, instant.getPrice()));
        }
        return result;
    }

    private List<CategoryDTO> getCategories() {
        List<CategoryDTO> result = new ArrayList<>();
        for (Category category : Services.get().market().getCategories()) {
            double change = 0.0d;
            try {
                if (!category.getItems().isEmpty()) change = category.getDayChange();
            } catch (RuntimeException ignored) {
                // A fresh database may not have enough day-history yet.
            }
            if (!Double.isFinite(change)) change = 0.0d;
            result.add(new CategoryDTO(category.getIdentifier(), category.getDisplayName(), change));
        }
        return result;
    }

    private List<PortfolioDTO> getTopPortfolios() {
        List<PortfolioDTO> result = new ArrayList<>();
        Database database = DatabaseManager.get().getDatabase();
        HashMap<UUID, Portfolio> top = database.getTopWorth(5);
        if (top == null) return result;

        for (Map.Entry<UUID, Portfolio> entry : top.entrySet()) {
            UUID uuid = entry.getKey();
            Portfolio portfolio = entry.getValue();
            if (uuid == null || portfolio == null) continue;

            String name = database.getNameByUUID(uuid);
            if (name == null || name.isBlank()) name = uuid.toString();
            double netValue = portfolio.getInventoryValue() - database.getDebt(uuid);
            result.add(new PortfolioDTO(name, netValue, portfolio.getContent()));
        }
        return result;
    }

    private Map<String, Object> getPublicConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("title", webConfig.title());
        config.put("accent", webConfig.accent());
        config.put("defaultMode", webConfig.defaultMode());
        config.put("lockMode", webConfig.lockMode());
        config.put("defaultTheme", webConfig.defaultTheme());
        return config;
    }

    private void serveIcon(Context ctx) {
        String identifier = ctx.pathParam("identifier");
        BufferedImage image = Services.get().images().getImage(identifier);

        if (image == null && (identifier.equals("logo") || identifier.equals("logo-color") || identifier.equals("fire"))) {
            File file = new File(plugin.getDataFolder(), "images/" + identifier + ".png");
            if (file.isFile()) {
                try {
                    image = ImageIO.read(file);
                } catch (IOException exception) {
                    plugin.getLogger().log(Level.WARNING, "Could not read web image " + file, exception);
                }
            }
        }

        if (image == null) {
            ctx.status(404).result("Image not found.");
            return;
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) {
                ctx.status(500).result("Failed to encode image to PNG.");
                return;
            }
            ctx.header("Cache-Control", "public, max-age=" + TimeUnit.HOURS.toSeconds(1));
            ctx.contentType("image/png");
            ctx.result(output.toByteArray());
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Error processing image " + identifier, exception);
            ctx.status(500).result("Error processing image.");
        }
    }

    private UUID getAuthenticatedPlayerUUID(Context ctx) {
        String sessionToken = ctx.cookie("nascraft_session");
        if (sessionToken == null) return null;
        String sessionHash = SecurityUtils.hashToken(sessionToken);
        UUID uuid = DatabaseManager.get().getDatabase().getWebSessionPlayerUUID(sessionHash);
        if (uuid == null) return null;
        
        DatabaseManager.get().getDatabase().updateWebSessionActivity(sessionHash, LocalDateTime.now());
        return uuid;
    }

    private Map<String, Object> getPortfolioData(UUID uuid) {
        Portfolio portfolio = me.bounser.nascraft.portfolio.PortfoliosManager.getInstance().getPortfolio(uuid);
        String username = DatabaseManager.get().getDatabase().getNameByUUID(uuid);
        if (username == null || username.isBlank()) {
            username = Bukkit.getOfflinePlayer(uuid).getName();
        }
        if (username == null) username = "Unknown Player";

        double worth = portfolio.getInventoryValue();
        double balance = MoneyManager.getInstance().getBalance(Bukkit.getOfflinePlayer(uuid), CurrenciesManager.getInstance().getDefaultCurrency());
        int unlocked = portfolio.getCapacity();
        int max = 27;
        double nextPrice = portfolio.getNextSlotPrice();

        Map<String, Object> resp = new HashMap<>();
        resp.put("username", username);
        resp.put("portfolioValue", worth);
        resp.put("unlockedSlots", unlocked);
        resp.put("maximumSlots", max);
        resp.put("nextSlotPrice", nextPrice);
        resp.put("balance", balance);

        List<Map<String, Object>> slots = new ArrayList<>();
        List<Item> items = new ArrayList<>(portfolio.getContent().keySet());

        for (int i = 0; i < max; i++) {
            Map<String, Object> slot = new HashMap<>();
            slot.put("slot", i);
            if (i < unlocked) {
                slot.put("locked", false);
                if (i < items.size()) {
                    Item item = items.get(i);
                    slot.put("item", item.getIdentifier());
                    slot.put("amount", portfolio.getContent().get(item));
                    slot.put("displayName", item.getName());
                    slot.put("price", item.getPrice().getValue());
                } else {
                    slot.put("item", null);
                    slot.put("amount", 0);
                }
            } else {
                slot.put("locked", true);
            }
            slots.add(slot);
        }
        resp.put("slots", slots);
        return resp;
    }

    public synchronized void stopServer() {
        if (webServer == null) return;
        try {
            webServer.stop();
            plugin.getLogger().info("Web server stopped.");
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Error stopping web server.", exception);
        } finally {
            webServer = null;
        }
    }

    public synchronized boolean isRunning() {
        return webServer != null;
    }
}
