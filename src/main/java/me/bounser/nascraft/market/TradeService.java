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

public class TradeService {

    private static final Object lock = new Object();

    public enum TradeError {
        MARKET_CLOSED,
        ITEM_NOT_FOUND,
        INVALID_AMOUNT,
        NOT_ENOUGH_MONEY,
        NOT_ENOUGH_PORTFOLIO_ITEMS,
        PORTFOLIO_FULL,
        CURRENCY_UNAVAILABLE,
        PRICE_LIMIT_REACHED,
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
        CompletableFuture<TradeResult> future = new CompletableFuture<>();
        
        FoliaScheduler.runGlobal(Nascraft.getInstance(), () -> {
            synchronized (lock) {
                try {
                    if (item == null) {
                        future.complete(new TradeResult(false, TradeError.ITEM_NOT_FOUND, 0));
                        return;
                    }

                    if (amount <= 0) {
                        future.complete(new TradeResult(false, TradeError.INVALID_AMOUNT, 0));
                        return;
                    }

                    if (!MarketManager.getInstance().getActive()) {
                        future.complete(new TradeResult(false, TradeError.MARKET_CLOSED, 0));
                        return;
                    }

                    boolean limitReached = !item.getPrice().canStockChange(amount, true);
                    if (limitReached && item.isPriceRestricted()) {
                        future.complete(new TradeResult(false, TradeError.PRICE_LIMIT_REACHED, 0));
                        return;
                    }

                    double worth = item.buyPrice(amount);
                    Currency currency = item.getCurrency();

                    if (!MoneyManager.getInstance().isCurrencyAvailable(currency)) {
                        future.complete(new TradeResult(false, TradeError.CURRENCY_UNAVAILABLE, 0));
                        return;
                    }

                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                    if (!MoneyManager.getInstance().hasEnoughMoney(offlinePlayer, currency, worth)) {
                        future.complete(new TradeResult(false, TradeError.NOT_ENOUGH_MONEY, 0));
                        return;
                    }

                    Portfolio portfolio = PortfoliosManager.getInstance().getPortfolio(uuid);
                    if (!portfolio.hasSpace(item, amount)) {
                        future.complete(new TradeResult(false, TradeError.PORTFOLIO_FULL, 0));
                        return;
                    }

                    // Withdraw money
                    MoneyManager.getInstance().withdraw(offlinePlayer, currency, worth, (1 - item.getPrice().getBuyTaxMultiplier()));

                    // Add items to portfolio
                    portfolio.addItem(item, amount);

                    // Update market stock
                    if (!limitReached) {
                        if (item.getParent() != null) {
                            item.getParent().updateInternalValues(amount,
                                    amount * item.getPrice().getValue(),
                                    -amount * item.getMultiplier(),
                                    item.getPrice().getValue() * (1 - item.getPrice().getBuyTaxMultiplier()) * amount * item.getMultiplier());
                        } else {
                            item.updateInternalValues(amount,
                                    amount * item.getPrice().getValue(),
                                    -amount * item.getMultiplier(),
                                    item.getPrice().getValue() * (1 - item.getPrice().getBuyTaxMultiplier()) * amount * item.getMultiplier());
                        }
                    }

                    // Save trade to database
                    Trade trade = new Trade(item, LocalDateTime.now(), worth, amount, true, false, uuid);
                    DatabaseManager.get().getDatabase().saveTrade(trade);

                    if (Config.getInstance().getDiscordEnabled() && Config.getInstance().getLogChannelEnabled()) {
                        DiscordLog.getInstance().sendTradeLog(trade);
                    }

                    MarketManager.getInstance().addOperation();

                    future.complete(new TradeResult(true, null, worth));

                } catch (Exception e) {
                    Nascraft.getInstance().getLogger().severe("Error executing buy transaction: " + e.getMessage());
                    future.complete(new TradeResult(false, TradeError.TRADE_FAILED, 0));
                }
            }
        });

        return future;
    }

    public static CompletableFuture<TradeResult> sellFromPortfolio(UUID uuid, Item item, int amount) {
        CompletableFuture<TradeResult> future = new CompletableFuture<>();

        FoliaScheduler.runGlobal(Nascraft.getInstance(), () -> {
            synchronized (lock) {
                try {
                    if (item == null) {
                        future.complete(new TradeResult(false, TradeError.ITEM_NOT_FOUND, 0));
                        return;
                    }

                    if (amount <= 0) {
                        future.complete(new TradeResult(false, TradeError.INVALID_AMOUNT, 0));
                        return;
                    }

                    Portfolio portfolio = PortfoliosManager.getInstance().getPortfolio(uuid);
                    if (!portfolio.hasItem(item, amount)) {
                        future.complete(new TradeResult(false, TradeError.NOT_ENOUGH_PORTFOLIO_ITEMS, 0));
                        return;
                    }

                    if (!MarketManager.getInstance().getActive()) {
                        future.complete(new TradeResult(false, TradeError.MARKET_CLOSED, 0));
                        return;
                    }

                    boolean limitReached = !item.getPrice().canStockChange(amount, false);
                    if (limitReached && item.isPriceRestricted()) {
                        future.complete(new TradeResult(false, TradeError.PRICE_LIMIT_REACHED, 0));
                        return;
                    }

                    double worth = item.sellPrice(amount);
                    Currency currency = item.getCurrency();

                    if (!MoneyManager.getInstance().isCurrencyAvailable(currency)) {
                        future.complete(new TradeResult(false, TradeError.CURRENCY_UNAVAILABLE, 0));
                        return;
                    }

                    // Remove items from portfolio
                    portfolio.removeItem(item, amount);

                    // Deposit money
                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                    MoneyManager.getInstance().deposit(offlinePlayer, currency, worth, item.getPrice().getSellTaxMultiplier());

                    // Update market stock
                    if (!limitReached) {
                        if (item.getParent() != null) {
                            item.getParent().updateInternalValues(amount,
                                    amount * item.getPrice().getValue(),
                                    amount * item.getMultiplier(),
                                    item.getPrice().getValue() * (1 - item.getPrice().getBuyTaxMultiplier()) * amount * item.getMultiplier());
                        } else {
                            item.updateInternalValues(amount,
                                    amount * item.getPrice().getValue(),
                                    amount * item.getMultiplier(),
                                    item.getPrice().getValue() * (1 - item.getPrice().getBuyTaxMultiplier()) * amount * item.getMultiplier());
                        }
                    }

                    // Save trade to database
                    Trade trade = new Trade(item, LocalDateTime.now(), worth, amount, false, false, uuid);
                    DatabaseManager.get().getDatabase().saveTrade(trade);

                    if (Config.getInstance().getDiscordEnabled() && Config.getInstance().getLogChannelEnabled()) {
                        DiscordLog.getInstance().sendTradeLog(trade);
                    }

                    MarketManager.getInstance().addOperation();

                    future.complete(new TradeResult(true, null, worth));

                } catch (Exception e) {
                    Nascraft.getInstance().getLogger().severe("Error executing sell transaction: " + e.getMessage());
                    future.complete(new TradeResult(false, TradeError.TRADE_FAILED, 0));
                }
            }
        });

        return future;
    }
}
