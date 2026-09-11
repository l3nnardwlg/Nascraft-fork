package me.bounser.nascraft.inventorygui;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.config.lang.Lang;
import me.bounser.nascraft.config.lang.Message;
import me.bounser.nascraft.market.unit.Item;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.ArrayList;
import java.util.List;

/** Paginated inventory UI for market search results. */
public class SearchResultsMenu implements MenuPage {

    public static final int[] RESULT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    public static final int PREVIOUS_SLOT = 45;
    public static final int BACK_SLOT = 48;
    public static final int QUERY_SLOT = 49;
    public static final int NEXT_SLOT = 53;

    private final Player player;
    private final String query;
    private final List<Item> results;
    private Inventory gui;

    public SearchResultsMenu(Player player, String query, List<Item> results) {
        this.player = player;
        this.query = query;
        this.results = new ArrayList<>(results);
        open();
    }

    @Override
    public void open() {
        gui = Bukkit.createInventory(null, 54, ChatColor.DARK_GRAY + "Market Search: " + ChatColor.GOLD + query);
        player.setMetadata("NascraftPage", new FixedMetadataValue(Nascraft.getInstance(), 0));
        update();
    }

    @Override
    public void close() {
        player.closeInventory();
    }

    @Override
    public void update() {
        int page = player.hasMetadata("NascraftPage") ? player.getMetadata("NascraftPage").get(0).asInt() : 0;
        int pageSize = RESULT_SLOTS.length;

        for (int slot : RESULT_SLOTS) gui.setItem(slot, new ItemStack(Material.AIR));

        for (int i = 0; i < pageSize; i++) {
            int resultIndex = page * pageSize + i;
            if (resultIndex >= results.size()) break;

            Item item = results.get(resultIndex);
            ItemStack icon = item.getItemStack().clone();
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName(item.getFormattedName());

            List<String> lore = MarketMenuManager.getInstance().getLoreFromItem(
                    item,
                    Lang.get().message(Message.GUI_CATEGORY_ITEM_LORE)
            );
            lore.add("");
            lore.add(ChatColor.GRAY + "Category: " + ChatColor.WHITE + item.getCategory().getFormattedDisplayName());
            lore.add(ChatColor.GREEN + "Click to open market item");
            meta.setLore(lore);
            icon.setItemMeta(meta);
            gui.setItem(RESULT_SLOTS[i], icon);
        }

        gui.setItem(PREVIOUS_SLOT, navigationItem(page > 0 ? Material.ARROW : Material.BARRIER,
                page > 0 ? "Previous page" : "No previous page"));
        gui.setItem(BACK_SLOT, navigationItem(Material.BARRIER, "Back to market"));
        gui.setItem(QUERY_SLOT, navigationItem(Material.COMPASS,
                "Search: " + query + " (" + results.size() + " result" + (results.size() == 1 ? "" : "s") + ")"));
        gui.setItem(NEXT_SLOT, navigationItem((page + 1) * pageSize < results.size() ? Material.ARROW : Material.BARRIER,
                (page + 1) * pageSize < results.size() ? "Next page" : "No next page"));

        if (player.getOpenInventory().getTopInventory() != gui) player.openInventory(gui);
        player.setMetadata("NascraftMenu", new FixedMetadataValue(Nascraft.getInstance(), "search-results"));
        player.setMetadata("NascraftSearchQuery", new FixedMetadataValue(Nascraft.getInstance(), query));
        player.setMetadata("NascraftPage", new FixedMetadataValue(Nascraft.getInstance(), page));
        MarketMenuManager.getInstance().setMenuOfPlayer(player, this);
    }

    public List<Item> getResults() {
        return results;
    }

    private ItemStack navigationItem(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + name);
        stack.setItemMeta(meta);
        return stack;
    }
}
