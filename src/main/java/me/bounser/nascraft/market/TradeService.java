package me.bounser.nascraft.market;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.database.DatabaseManager;
import me.bounser.nascraft.database.commands.resources.Trade;
import me.bounser.nascraft.discord.DiscordLog;
import me.bounser.nascraft.managers.MoneyManager;
import me.bounser.nascraft.managers.currencies.Currency;
import me.bounser.nascraft.market.unit.Item;
import me.bounser.nascraft.portfolio.Portfolio;
import me.bounser.nascraft.portfolio.PortfoliosManager;
import me.bounser.nascraft.scheduler.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class TradeService {

    private static final long DUPLICATE_WINDOW_MS = 1_500L;
    private static final ConcurrentHashMap<UUID, Object> PLAYER_LOCKS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> RECENT_TRADES = new ConcurrentHashMap<>();

    public enum TradeError {
        MARKET_CLOSED,
        ITEM_NOT_FOUND,
        INVALID_AMOUNT,
        NOT_ENOUGH_MONEY,
        NOT_ENOUGH_PORTFOLIO_ITEMS,
        PORTFOLIO_FULL,
        CURRENCY_UNAVAILABLE,
        PRICE_LIMIT_REACHED,
        DUPLICATE_REQUEST,
        TRADE_FAILED
    }

    public static class TradeResult {
        public final boolean success;
        public final TradeError error;
        public final double worth;

        public TradeResult(boolean success, TradeError error, double worth) {
            this.success = success;
            this.error = error;
            this.worth = worth;
        }
    }

    public static CompletableFuture<TradeResult> buyToPortfolio(UUID uuid, Item item, int amount) {
        return execute(uuid, item, amount, true);
    }

    public static CompletableFuture<TradeResult> sellFromPortfolio(UUID uuid, Item item, int amount) {
        return execute(uuid, item, amount, false);
    }

    private static CompletableFuture<TradeResult> execute(UUID uuid, Item item, int amount, boolean buy) {
        CompletableFuture<TradeResult> future = new CompletableFuture<>();

        FoliaScheduler.runGlobal(Nascraft.getInstance(), () -> {
            Object playerLock = PLAYER_LOCKS.computeIfAbsent(uuid, ignored -> new Object());
            synchronized (playerLock) {
                try {
                    TradeResult result = buy
                            ? executeBuy(uuid, item, amount)
                            : executeSell(uuid, item, amount);
                    future.complete(result);
                } catch (Exception e) {
                    Nascraft.getInstance().getLogger().severe(
                            "Error executing " + (buy ? "buy" : "sell") + " transaction: " + e.getMessage()
                    );
                    future.complete(new TradeResult(false, TradeError.TRADE_FAILED, 0));
                }
            }
        });

        return future;
    }

    private static TradeResult executeBuy(UUID uuid, Item item, int amount) {
        TradeResult validation = validateCommon(uuid, item, amount, true);
        if (validation != null) return validation;

        String fingerprint = fingerprint(uuid, item, amount, true);
        if (isDuplicate(fingerprint)) return new TradeResult(false, TradeError.DUPLICATE_REQUEST, 0);

        boolean limitReached = !item.getPrice().canStockChange(amount, true);
        double worth = item.buyPrice(amount);
        Currency currency = item.getCurrency();
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);

        if (!MoneyManager.getInstance().isCurrencyAvailable(currency)) {
            return new TradeResult(false, TradeError.CURRENCY_UNAVAILABLE, 0);
        }
        if (!MoneyManager.getInstance().hasEnoughMoney(offlinePlayer, currency, worth)) {
            return new TradeResult(false, TradeError.NOT_ENOUGH_MONEY, 0);
        }

        Portfolio portfolio = PortfoliosManager.getInstance().getPortfolio(uuid);
        if (!portfolio.hasSpace(item, amount)) {
            return new TradeResult(false, TradeError.PORTFOLIO_FULL, 0);
        }

        MoneyManager.getInstance().withdraw(
                offlinePlayer,
                currency,
                worth,
                (1 - item.getPrice().getBuyTaxMultiplier())
        );

        portfolio.addItem(item, amount);
        updateMarket(item, amount, true, limitReached);
        saveTrade(uuid, item, amount, worth, true);
        markCompleted(fingerprint);
        return new TradeResult(true, null, worth);
    }

    private static TradeResult executeSell(UUID uuid, Item item, int amount) {
        TradeResult validation = validateCommon(uuid, item, amount, false);
        if (validation != null) return validation;

        String fingerprint = fingerprint(uuid, item, amount, false);
        if (isDuplicate(fingerprint)) return new TradeResult(false, TradeError.DUPLICATE_REQUEST, 0);

        Portfolio portfolio = PortfoliosManager.getInstance().getPortfolio(uuid);
        if (!portfolio.hasItem(item, amount)) {
            return new TradeResult(false, TradeError.NOT_ENOUGH_PORTFOLIO_ITEMS, 0);
        }

        boolean limitReached = !item.getPrice().canStockChange(amount, false);
        double worth = item.sellPrice(amount);
        Currency currency = item.getCurrency();

        if (!MoneyManager.getInstance().isCurrencyAvailable(currency)) {
            return new TradeResult(false, TradeError.CURRENCY_UNAVAILABLE, 0);
        }

        portfolio.removeItem(item, amount);
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        MoneyManager.getInstance().deposit(offlinePlayer, currency, worth, item.getPrice().getSellTaxMultiplier());
        updateMarket(item, amount, false, limitReached);
        saveTrade(uuid, item, amount, worth, false);
        markCompleted(fingerprint);
        return new TradeResult(true, null, worth);
    }

    private static TradeResult validateCommon(UUID uuid, Item item, int amount, boolean buy) {
        if (uuid == null || item == null) return new TradeResult(false, TradeError.ITEM_NOT_FOUND, 0);
        if (amount <= 0) return new TradeResult(false, TradeError.INVALID_AMOUNT, 0);
        if (!MarketManager.getInstance().getActive()) return new TradeResult(false, TradeError.MARKET_CLOSED, 0);

        boolean limitReached = !item.getPrice().canStockChange(amount, buy);
        if (limitReached && item.isPriceRestricted()) {
            return new TradeResult(false, TradeError.PRICE_LIMIT_REACHED, 0);
        }
        return null;
    }

    private static void updateMarket(Item item, int amount, boolean buy, boolean limitReached) {
        if (limitReached) return;

        double direction = buy ? -1.0 : 1.0;
        Item target = item.getParent() != null ? item.getParent() : item;
        target.updateInternalValues(
                amount,
                amount * item.getPrice().getValue(),
                direction * amount * item.getMultiplier(),
                item.getPrice().getValue()
                        * (1 - item.getPrice().getBuyTaxMultiplier())
                        * amount
                        * item.getMultiplier()
        );
    }

    private static void saveTrade(UUID uuid, Item item, int amount, double worth, boolean buy) {
        Trade trade = new Trade(item, LocalDateTime.now(), worth, amount, buy, false, uuid);
        DatabaseManager.get().getDatabase().saveTrade(trade);

        if (Config.getInstance().getDiscordEnabled() && Config.getInstance().getLogChannelEnabled()) {
            DiscordLog.getInstance().sendTradeLog(trade);
        }

        MarketManager.getInstance().addOperation();
    }

    private static String fingerprint(UUID uuid, Item item, int amount, boolean buy) {
        return uuid + ":" + (buy ? "BUY" : "SELL") + ":" + item.getIdentifier() + ":" + amount;
    }

    private static boolean isDuplicate(String fingerprint) {
        long now = System.currentTimeMillis();
        RECENT_TRADES.entrySet().removeIf(entry -> now - entry.getValue() > DUPLICATE_WINDOW_MS * 4);
        Long previous = RECENT_TRADES.get(fingerprint);
        return previous != null && now - previous < DUPLICATE_WINDOW_MS;
    }

    private static void markCompleted(String fingerprint) {
        RECENT_TRADES.put(fingerprint, System.currentTimeMillis());
    }
}
