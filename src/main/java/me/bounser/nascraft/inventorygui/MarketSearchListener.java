package me.bounser.nascraft.inventorygui;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.input.ChatInputManager;
import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.unit.Item;
import me.bounser.nascraft.scheduler.FoliaScheduler;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MarketSearchListener implements Listener {
    private static final int SEARCH_SLOT = 0;
    private static final String SEARCH_NAME = ChatColor.GOLD + "§lSearch Item";

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getInventory().getSize() != Config.getInstance().getMainMenuSize()) return;

        FoliaScheduler.runAtEntityLater(Nascraft.getInstance(), player, () -> {
            if (!player.hasMetadata("NascraftMenu")
                    || !"main-menu".equals(player.getMetadata("NascraftMenu").get(0).asString())
                    || player.getOpenInventory().getTopInventory().getSize() != Config.getInstance().getMainMenuSize()) return;

            ItemStack search = new ItemStack(Material.COMPASS);
            ItemMeta meta = search.getItemMeta();
            meta.setDisplayName(SEARCH_NAME);
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

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!player.hasMetadata("NascraftMenu")) return;

        String closedMenu = player.getMetadata("NascraftMenu").get(0).asString();
        Inventory closedInventory = event.getInventory();

        // Menu refreshes in several Nascraft GUIs intentionally reopen the same
        // Inventory instance. That also fires InventoryCloseEvent, but it is not a
        // real close and must not clear NascraftMenu. Defer one tick so Paper has
        // finished the transition, then keep the state if that exact inventory is
        // still the player's top inventory.
        FoliaScheduler.runAtEntityLater(Nascraft.getInstance(), player, () -> {
            if (!player.isOnline() || !player.hasMetadata("NascraftMenu")) return;

            if (player.getOpenInventory().getTopInventory() == closedInventory) return;

            String currentMenu = player.getMetadata("NascraftMenu").get(0).asString();
            if (!closedMenu.equals(currentMenu)) return;

            player.removeMetadata("NascraftMenu", Nascraft.getInstance());
            if (player.hasMetadata("NascraftQuantity")) {
                player.removeMetadata("NascraftQuantity", Nascraft.getInstance());
            }
            if (player.hasMetadata("NascraftPage")) {
                player.removeMetadata("NascraftPage", Nascraft.getInstance());
            }
            MarketMenuManager.getInstance().removeMenuFromPlayer(player);
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() != SEARCH_SLOT) return;

        // A click in a category can open the main menu earlier in the same event.
        // Never treat that old click as a search click just because the metadata
        // has already changed to main-menu.
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() != Material.COMPASS || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        if (!meta.hasDisplayName() || !SEARCH_NAME.equals(meta.getDisplayName())) return;

        if (!player.hasMetadata("NascraftMenu")
                || !"main-menu".equals(player.getMetadata("NascraftMenu").get(0).asString())) return;

        event.setCancelled(true);
        openSearch(player);
    }

    private void openSearch(Player player) {
        ChatInputManager.getInstance().request(player, "Enter the market item to search for.", raw -> {
            String query = normalize(raw);
            if (query.isBlank()) {
                player.sendMessage(ChatColor.RED + "Enter an item name.");
                reopenMarket(player);
                return;
            }

            Item match = findBestMatch(query);
            if (match == null) {
                player.sendMessage(ChatColor.RED + "No market item found for: " + raw);
                reopenMarket(player);
                return;
            }

            MarketMenuManager.getInstance().setMenuOfPlayer(player, new BuySellMenu(player, match));
        }, () -> reopenMarket(player));
    }

    private void reopenMarket(Player player) { player.performCommand("market"); }

    private Item findBestMatch(String query) {
        return MarketManager.getInstance().getAllItems().stream()
                .filter(item -> matches(item, query))
                .min(Comparator.comparingInt((Item item) -> matchRank(item, query))
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
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
    }
}
