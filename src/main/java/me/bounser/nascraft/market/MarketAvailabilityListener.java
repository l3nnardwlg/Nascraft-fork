package me.bounser.nascraft.market;

import me.bounser.nascraft.api.events.BuyItemEvent;
import me.bounser.nascraft.api.events.SellItemEvent;
import me.bounser.nascraft.config.Config;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

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

    private boolean isEnabled(String identifier, String key) {
        return Config.getInstance().getItemsFileConfiguration()
                .getBoolean("items." + identifier + "." + key, true);
    }
}
