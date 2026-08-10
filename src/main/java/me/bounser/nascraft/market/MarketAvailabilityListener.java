package me.bounser.nascraft.market;

import me.bounser.nascraft.api.events.BuyItemEvent;
import me.bounser.nascraft.api.events.SellItemEvent;
import me.bounser.nascraft.api.events.TransactionCompletedEvent;
import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.database.DatabaseManager;
import me.bounser.nascraft.market.unit.Item;
import me.bounser.nascraft.market.unit.stats.Instant;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.time.LocalDateTime;

public final class MarketAvailabilityListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBuy(BuyItemEvent event) {
        String identifier = event.getItem().getIdentifier();
        if (isEnabled(identifier, "buy-enabled")) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (player != null) {
            player.sendMessage(ChatColor.RED + "Buying " + event.getItem().getName() + " is currently disabled by a market administrator.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSell(SellItemEvent event) {
        String identifier = event.getItem().getIdentifier();
        if (isEnabled(identifier, "sell-enabled")) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (player != null) {
            player.sendMessage(ChatColor.RED + "Selling " + event.getItem().getName() + " is currently disabled by a market administrator.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTransactionCompleted(TransactionCompletedEvent event) {
        Item traded = event.getItem();
        Item parent = traded.isParent() ? traded : traded.getParent();
        if (parent == null) return;

        DatabaseManager.get().getDatabase().saveDayPrice(
                parent,
                new Instant(LocalDateTime.now(), parent.getPrice().getValue(), Math.max(1, Math.round(event.getAmount())))
        );
        DatabaseManager.get().getDatabase().saveItem(parent);
    }

    private boolean isEnabled(String identifier, String key) {
        return Config.getInstance().getItemsFileConfiguration()
                .getBoolean("items." + identifier + "." + key, true);
    }
}
