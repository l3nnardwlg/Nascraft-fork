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
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MarketSearchListener implements Listener {
    private static final int SEARCH_SLOT = 0;
    @EventHandler public void onOpen(InventoryOpenEvent event) { if (!(event.getPlayer() instanceof Player player)) return; if (event.getInventory().getSize() != Config.getInstance().getMainMenuSize()) return; FoliaScheduler.runAtEntityLater(Nascraft.getInstance(), player, () -> { if (!player.hasMetadata("NascraftMenu") || !"main-menu".equals(player.getMetadata("NascraftMenu").get(0).asString()) || player.getOpenInventory().getTopInventory().getSize() != Config.getInstance().getMainMenuSize()) return; ItemStack search = new ItemStack(Material.COMPASS); ItemMeta meta = search.getItemMeta(); meta.setDisplayName(ChatColor.GOLD + "§lSearch Item"); meta.setLore(List.of(ChatColor.GRAY + "Search by item name, alias", ChatColor.GRAY + "or market identifier.", "", ChatColor.GREEN + "§lCLICK TO SEARCH")); search.setItemMeta(meta); player.getOpenInventory().getTopInventory().setItem(SEARCH_SLOT, search); }, 1L); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onClick(InventoryClickEvent event) { if (!(event.getWhoClicked() instanceof Player player)) return; if (!player.hasMetadata("NascraftMenu") || !"main-menu".equals(player.getMetadata("NascraftMenu").get(0).asString()) || event.getRawSlot() != SEARCH_SLOT) return; event.setCancelled(true); openSearch(player); }
    private void openSearch(Player player) { ChatInputManager.getInstance().request(player, "Enter the market item to search for.", raw -> { String query = normalize(raw); if (query.isBlank()) { player.sendMessage(ChatColor.RED + "Enter an item name."); reopenMarket(player); return; } Item match = findBestMatch(query); if (match == null) { player.sendMessage(ChatColor.RED + "No market item found for: " + raw); reopenMarket(player); return; } MarketMenuManager.getInstance().setMenuOfPlayer(player, new BuySellMenu(player, match)); }, () -> reopenMarket(player)); }
    private void reopenMarket(Player player) { player.performCommand("market"); }
    private Item findBestMatch(String query) { return MarketManager.getInstance().getAllItems().stream().filter(item -> matches(item, query)).min(Comparator.comparingInt((Item item) -> matchRank(item, query)).thenComparing(item -> item.getName().toLowerCase(Locale.ROOT))).orElse(null); }
    private boolean matches(Item item, String query) { return normalize(item.getIdentifier()).contains(query) || normalize(item.getName()).contains(query) || normalize(item.getItemStack().getType().name()).contains(query); }
    private int matchRank(Item item, String query) { String identifier = normalize(item.getIdentifier()), name = normalize(item.getName()), material = normalize(item.getItemStack().getType().name()); if (identifier.equals(query) || name.equals(query) || material.equals(query)) return 0; if (identifier.startsWith(query) || name.startsWith(query) || material.startsWith(query)) return 1; return 2; }
    private String normalize(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).trim().replace(' ', '_'); }
}
