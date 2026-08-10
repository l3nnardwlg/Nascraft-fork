package me.bounser.nascraft.inventorygui.admin;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.commands.admin.marketeditor.overview.MarketEditorManager;
import me.bounser.nascraft.database.DatabaseManager;
import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.unit.Item;
import me.bounser.nascraft.market.unit.Price;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class MarketAdminListener implements Listener {

    public static final String PERMISSION = "nascraft.market.admin";

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();
        boolean dashboard = MarketAdminMenu.TITLE.equals(title);
        boolean raise = "§8§lRaise Market Prices".equals(title);
        boolean lower = "§8§lLower Market Prices".equals(title);
        if (!dashboard && !raise && !lower) return;

        event.setCancelled(true);
        if (!player.hasPermission(PERMISSION)) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "You do not have permission to manage the market.");
            return;
        }

        int slot = event.getRawSlot();
        if (dashboard) {
            switch (slot) {
                case 11 -> MarketAdminMenu.openManipulation(player, false);
                case 13 -> MarketEditorManager.getInstance().startEditing(player);
                case 15 -> MarketAdminMenu.openManipulation(player, true);
                case 22 -> player.closeInventory();
                default -> { }
            }
            return;
        }

        if (slot == 22) {
            MarketAdminMenu.open(player);
            return;
        }

        double percentage = switch (slot) {
            case 10 -> 0.01;
            case 12 -> 0.05;
            case 14 -> 0.10;
            case 16 -> 0.25;
            default -> 0.0;
        };
        if (percentage <= 0) return;

        double factor = raise ? 1.0 + percentage : 1.0 - percentage;
        int changed = applyMarketFactor(factor);
        player.sendMessage((raise ? ChatColor.GREEN : ChatColor.RED)
                + "Market prices " + (raise ? "raised" : "lowered") + " by "
                + (int) Math.round(percentage * 100) + "% (" + changed + " items)." );
        Nascraft.getInstance().getLogger().info("[Market Admin] " + player.getName() + " "
                + (raise ? "raised" : "lowered") + " all market prices by "
                + (int) Math.round(percentage * 100) + "%.");
        MarketAdminMenu.openManipulation(player, raise);
    }

    private int applyMarketFactor(double factor) {
        int changed = 0;
        for (Item item : MarketManager.getInstance().getAllParentItems()) {
            Price price = item.getPrice();
            if (price.getElasticity() == 0) continue;

            double target = price.getValue() * factor;
            double targetStock = price.getStockFromValue(target);
            if (!Double.isFinite(targetStock)) continue;

            price.setStock((float) targetStock);
            price.setVersion(price.getVersion() + 1);
            DatabaseManager.get().getDatabase().saveItem(item);
            changed++;
        }
        return changed;
    }
}
