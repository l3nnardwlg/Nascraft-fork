package me.bounser.nascraft.inventorygui;

import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.unit.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/** Handles navigation from the native item chart back to its market item. */
public final class InfoMenuReturnListener implements Listener {

    private static final String PREFIX = "info-menu-";
    private static final int RETURN_SLOT = 45;

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !player.hasMetadata("NascraftMenu")) return;

        String metadata = player.getMetadata("NascraftMenu").get(0).asString();
        if (!metadata.startsWith(PREFIX)) return;

        event.setCancelled(true);
        if (event.getRawSlot() != RETURN_SLOT) return;

        Item item = MarketManager.getInstance().getItem(metadata.substring(PREFIX.length()));
        if (item == null) {
            MarketMenuManager.getInstance().setMenuOfPlayer(player, new MainMenu(player));
            return;
        }

        MarketMenuManager.getInstance().setMenuOfPlayer(player, new BuySellMenu(player, item));
    }
}
