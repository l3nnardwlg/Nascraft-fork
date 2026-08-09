package me.bounser.nascraft.inventorygui.admin;

import me.bounser.nascraft.Nascraft;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

public final class MarketAdminMenu {

    public static final String TITLE = "§8§lMarket Admin";
    private static boolean listenerRegistered = false;

    private MarketAdminMenu() {}

    public static void open(Player player) {
        ensureListenerRegistered();

        Inventory inventory = Bukkit.createInventory(player, 27, TITLE);

        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);

        inventory.setItem(11, item(Material.REDSTONE,
                ChatColor.RED + "§lLOWER PRICES",
                ChatColor.GRAY + "Manipulate the complete market downwards.",
                "",
                ChatColor.YELLOW + "Click to choose a percentage."));

        inventory.setItem(13, item(Material.CHEST,
                ChatColor.GOLD + "§lEDIT MARKET",
                ChatColor.GRAY + "No-code market editor.",
                ChatColor.GRAY + "Add items by drag & drop, remove items,",
                ChatColor.GRAY + "change prices and item properties.",
                "",
                ChatColor.GREEN + "Click to open the editor."));

        inventory.setItem(15, item(Material.EMERALD,
                ChatColor.GREEN + "§lRAISE PRICES",
                ChatColor.GRAY + "Manipulate the complete market upwards.",
                "",
                ChatColor.YELLOW + "Click to choose a percentage."));

        inventory.setItem(22, item(Material.BARRIER,
                ChatColor.RED + "§lCLOSE"));

        player.openInventory(inventory);
    }

    public static void openManipulation(Player player, boolean increase) {
        ensureListenerRegistered();
        String title = increase ? "§8§lRaise Market Prices" : "§8§lLower Market Prices";
        Inventory inventory = Bukkit.createInventory(player, 27, title);
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);

        double[] percentages = {0.01, 0.05, 0.10, 0.25};
        int[] slots = {10, 12, 14, 16};
        Material material = increase ? Material.LIME_DYE : Material.RED_DYE;
        ChatColor color = increase ? ChatColor.GREEN : ChatColor.RED;

        for (int i = 0; i < percentages.length; i++) {
            int percent = (int) Math.round(percentages[i] * 100);
            inventory.setItem(slots[i], item(material,
                    color + "§l" + (increase ? "+" : "-") + percent + "%",
                    ChatColor.GRAY + "Apply to every parent market item.",
                    ChatColor.DARK_GRAY + "The new values are persisted immediately.",
                    "",
                    ChatColor.YELLOW + "Click to apply."));
        }

        inventory.setItem(22, item(Material.ARROW,
                ChatColor.YELLOW + "§lBACK"));
        player.openInventory(inventory);
    }

    private static synchronized void ensureListenerRegistered() {
        if (listenerRegistered) return;
        Bukkit.getPluginManager().registerEvents(new MarketAdminListener(), Nascraft.getInstance());
        listenerRegistered = true;
    }

    private static ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(Arrays.asList(lore));
        stack.setItemMeta(meta);
        return stack;
    }
}
