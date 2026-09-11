package me.bounser.nascraft.inventorygui;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.input.ChatInputManager;
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
import org.bukkit.metadata.FixedMetadataValue;

import java.util.List;

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
                    ChatColor.GRAY + "Search all market items.",
                    ChatColor.GRAY + "Type a name directly in chat",
                    ChatColor.GRAY + "or use /search <item>.",
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

        FoliaScheduler.runAtEntityLater(Nascraft.getInstance(), player, () -> {
            if (!player.isOnline() || !player.hasMetadata("NascraftMenu")) return;
            if (player.getOpenInventory().getTopInventory() == closedInventory) return;

            String currentMenu = player.getMetadata("NascraftMenu").get(0).asString();
            if (!closedMenu.equals(currentMenu)) return;

            player.removeMetadata("NascraftMenu", Nascraft.getInstance());
            if (player.hasMetadata("NascraftQuantity")) player.removeMetadata("NascraftQuantity", Nascraft.getInstance());
            if (player.hasMetadata("NascraftPage")) player.removeMetadata("NascraftPage", Nascraft.getInstance());
            if (player.hasMetadata("NascraftSearchQuery")) player.removeMetadata("NascraftSearchQuery", Nascraft.getInstance());
            MarketMenuManager.getInstance().removeMenuFromPlayer(player);
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (player.hasMetadata("NascraftMenu")
                && "search-results".equals(player.getMetadata("NascraftMenu").get(0).asString())) {
            event.setCancelled(true);
            handleSearchResultsClick(player, event.getRawSlot());
            return;
        }

        if (event.getRawSlot() != SEARCH_SLOT) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() != Material.COMPASS || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        if (!meta.hasDisplayName() || !SEARCH_NAME.equals(meta.getDisplayName())) return;

        if (!player.hasMetadata("NascraftMenu")
                || !"main-menu".equals(player.getMetadata("NascraftMenu").get(0).asString())) return;

        event.setCancelled(true);
        openSearch(player);
    }

    private void handleSearchResultsClick(Player player, int slot) {
        MenuPage page = MarketMenuManager.getInstance().getMenuFromPlayer(player);
        if (!(page instanceof SearchResultsMenu searchMenu)) return;

        int currentPage = player.hasMetadata("NascraftPage") ? player.getMetadata("NascraftPage").get(0).asInt() : 0;

        if (slot == SearchResultsMenu.BACK_SLOT) {
            MarketMenuManager.getInstance().setMenuOfPlayer(player, new MainMenu(player));
            return;
        }

        if (slot == SearchResultsMenu.PREVIOUS_SLOT && currentPage > 0) {
            player.setMetadata("NascraftPage", new FixedMetadataValue(Nascraft.getInstance(), currentPage - 1));
            searchMenu.update();
            return;
        }

        if (slot == SearchResultsMenu.NEXT_SLOT
                && (currentPage + 1) * SearchResultsMenu.RESULT_SLOTS.length < searchMenu.getResults().size()) {
            player.setMetadata("NascraftPage", new FixedMetadataValue(Nascraft.getInstance(), currentPage + 1));
            searchMenu.update();
            return;
        }

        for (int i = 0; i < SearchResultsMenu.RESULT_SLOTS.length; i++) {
            if (SearchResultsMenu.RESULT_SLOTS[i] != slot) continue;
            int resultIndex = currentPage * SearchResultsMenu.RESULT_SLOTS.length + i;
            if (resultIndex >= searchMenu.getResults().size()) return;
            MarketMenuManager.getInstance().setMenuOfPlayer(player, new BuySellMenu(player, searchMenu.getResults().get(resultIndex)));
            return;
        }
    }

    private void openSearch(Player player) {
        ChatInputManager.getInstance().request(player, "Enter the market item to search for (or use /search <item>).", raw -> {
            List<Item> matches = MarketSearchService.findMatches(raw);
            if (matches.isEmpty()) {
                player.sendMessage(ChatColor.RED + "No market items found for: " + raw);
                reopenMarket(player);
                return;
            }

            new SearchResultsMenu(player, raw.trim(), matches);
        }, () -> reopenMarket(player));
    }

    private void reopenMarket(Player player) { player.performCommand("market"); }
}
