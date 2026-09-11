package me.bounser.nascraft.inventorygui;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.config.Messages;
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
        gui = Bukkit.createInventory(null, 54,
                Messages.get().legacy("search.results.title", "[QUERY]", query));
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
            if (item == null || item.getItemStack() == null) continue;

            ItemStack icon = item.getItemStack().clone();
            ItemMeta meta = icon.getItemMeta();
            if (meta == null) continue;
            meta.setDisplayName(item.getFormattedName());

            List<String> lore = MarketMenuManager.getInstance().getLoreFromItem(
                    item,
                    Lang.get().message(Message.GUI_CATEGORY_ITEM_LORE)
            );
            lore.add("");
            String categoryName = item.getCategory() == null
                    ? Messages.get().legacy("search.results.uncategorized")
                    : item.getCategory().getFormattedDisplayName();
            lore.add(Messages.get().legacy("search.results.category",
                    "[CATEGORY]", ChatColor.stripColor(categoryName) == null ? categoryName : ChatColor.stripColor(categoryName)));
            lore.add(Messages.get().legacy("search.results.open"));
            meta.setLore(lore);
            icon.setItemMeta(meta);
            gui.setItem(RESULT_SLOTS[i], icon);
        }

        gui.setItem(PREVIOUS_SLOT, navigationItem(page > 0 ? Material.ARROW : Material.BARRIER,
                page > 0 ? "search.results.previous" : "search.results.no-previous"));
        gui.setItem(BACK_SLOT, navigationItem(Material.BARRIER, "search.results.back"));
        gui.setItem(QUERY_SLOT, navigationItem(Material.COMPASS, "search.results.query",
                "[QUERY]", query,
                "[COUNT]", String.valueOf(results.size()),
                "[PLURAL]", results.size() == 1 ? "" : "s"));
        gui.setItem(NEXT_SLOT, navigationItem((page + 1) * pageSize < results.size() ? Material.ARROW : Material.BARRIER,
                (page + 1) * pageSize < results.size() ? "search.results.next" : "search.results.no-next"));

        if (player.getOpenInventory().getTopInventory() != gui) player.openInventory(gui);
        player.setMetadata("NascraftMenu", new FixedMetadataValue(Nascraft.getInstance(), "search-results"));
        player.setMetadata("NascraftSearchQuery", new FixedMetadataValue(Nascraft.getInstance(), query));
        player.setMetadata("NascraftPage", new FixedMetadataValue(Nascraft.getInstance(), page));
        MarketMenuManager.getInstance().setMenuOfPlayer(player, this);
    }

    public List<Item> getResults() {
        return results;
    }

    private ItemStack navigationItem(Material material, String path, String... replacements) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        meta.setDisplayName(Messages.get().legacy(path, replacements));
        stack.setItemMeta(meta);
        return stack;
    }
}
