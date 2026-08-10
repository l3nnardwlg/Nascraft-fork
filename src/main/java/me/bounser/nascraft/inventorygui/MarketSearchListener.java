package me.bounser.nascraft.inventorygui;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.unit.Item;
import me.bounser.nascraft.scheduler.FoliaScheduler;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Adds a lightweight search entry to the normal market without changing user configs. */
public class MarketSearchListener implements Listener {

    private static final int SEARCH_SLOT = 0;

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getInventory().getSize() != Config.getInstance().getMainMenuSize()) return;

        FoliaScheduler.runAtEntityLater(Nascraft.getInstance(), player, () -> {
            if (!player.hasMetadata("NascraftMenu")) return;
            if (!"main-menu".equals(player.getMetadata("NascraftMenu").get(0).asString())) return;
            if (player.getOpenInventory().getTopInventory().getSize() != Config.getInstance().getMainMenuSize()) return;

            ItemStack search = new ItemStack(Material.COMPASS);
            ItemMeta meta = search.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + "§lSearch Item");
            meta.setLore(List.of(
                    ChatColor.GRAY + "Search by item name, alias",
                    ChatColor.GRAY + "or market identifier.",
                    "",
                    ChatColor.GREEN + "§lCLICK TO SEARCH"
            ));
            search.setItemMeta(meta);
            player.getOpenInventory().getTopInventory().setItem(SEARCH_SLOT, search);
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.hasMetadata("NascraftMenu")) return;
        if (!"main-menu".equals(player.getMetadata("NascraftMenu").get(0).asString())) return;
        if (event.getRawSlot() != SEARCH_SLOT) return;

        event.setCancelled(true);
        openSearch(player);
    }

    private void openSearch(Player player) {
        new AnvilGUI.Builder()
                .onClick((slot, snapshot) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

                    String query = normalize(snapshot.getText());
                    if (query.isBlank() || query.equals("search")) {
                        return List.of(AnvilGUI.ResponseAction.replaceInputText("Enter an item name"));
                    }

                    Item match = findBestMatch(query);
                    if (match == null) {
                        return List.of(AnvilGUI.ResponseAction.replaceInputText("No item found"));
                    }

                    return Arrays.asList(
                            AnvilGUI.ResponseAction.close(),
                            AnvilGUI.ResponseAction.run(() ->
                                    MarketMenuManager.getInstance().setMenuOfPlayer(player, new BuySellMenu(player, match)))
                    );
                })
                .text("Search")
                .title("Search market item")
                .plugin(Nascraft.getInstance())
                .open(player);
    }

    private Item findBestMatch(String query) {
        return MarketManager.getInstance().getAllItems().stream()
                .filter(item -> matches(item, query))
                .min(Comparator
                        .comparingInt((Item item) -> matchRank(item, query))
                        .thenComparing(item -> item.getName().toLowerCase(Locale.ROOT)))
                .orElse(null);
    }

    private boolean matches(Item item, String query) {
        return normalize(item.getIdentifier()).contains(query)
                || normalize(item.getName()).contains(query)
                || normalize(item.getItemStack().getType().name()).contains(query);
    }

    private int matchRank(Item item, String query) {
        String identifier = normalize(item.getIdentifier());
        String name = normalize(item.getName());
        String material = normalize(item.getItemStack().getType().name());

        if (identifier.equals(query) || name.equals(query) || material.equals(query)) return 0;
        if (identifier.startsWith(query) || name.startsWith(query) || material.startsWith(query)) return 1;
        return 2;
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
    }
}
