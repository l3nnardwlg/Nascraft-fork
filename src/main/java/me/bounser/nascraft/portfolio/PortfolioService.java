package me.bounser.nascraft.portfolio;

import me.bounser.nascraft.managers.MoneyManager;
import me.bounser.nascraft.managers.currencies.CurrenciesManager;
import me.bounser.nascraft.managers.currencies.Currency;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

public class PortfolioService {

    public static Portfolio getPortfolio(UUID uuid) {
        return PortfoliosManager.getInstance().getPortfolio(uuid);
    }

    public static int getUnlockedSlots(UUID uuid) {
        return getPortfolio(uuid).getCapacity();
    }

    public static int getMaximumSlots(UUID uuid) {
        return 27; // maximum slots allowed in Nascraft GUI (slots 9 to 35)
    }

    public static double getNextSlotPrice(UUID uuid) {
        return getPortfolio(uuid).getNextSlotPrice();
    }

    public static boolean canUnlockNextSlot(UUID uuid) {
        Portfolio portfolio = getPortfolio(uuid);
        if (portfolio.getCapacity() >= 27) return false;
        
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        Currency currency = CurrenciesManager.getInstance().getDefaultCurrency();
        double price = portfolio.getNextSlotPrice();
        
        return MoneyManager.getInstance().hasEnoughMoney(offlinePlayer, currency, price);
    }

    public static boolean unlockNextSlot(UUID uuid) {
        Portfolio portfolio = getPortfolio(uuid);
        if (portfolio.getCapacity() >= 27) return false;
        
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        Currency currency = CurrenciesManager.getInstance().getDefaultCurrency();
        double price = portfolio.getNextSlotPrice();
        
        if (MoneyManager.getInstance().hasEnoughMoney(offlinePlayer, currency, price)) {
            MoneyManager.getInstance().simpleWithdraw(offlinePlayer, currency, price);
            portfolio.increaseCapacity();
            return true;
        }
        return false;
    }
}
