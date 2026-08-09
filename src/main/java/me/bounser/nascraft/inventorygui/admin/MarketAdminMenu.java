package me.bounser.nascraft.inventorygui.admin;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.commands.admin.marketeditor.edit.category.CategoryEditorListener;
import me.bounser.nascraft.commands.admin.marketeditor.edit.item.EditItemMenuListener;
import me.bounser.nascraft.commands.admin.marketeditor.overview.MarketEditorInvListener;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredListener;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class MarketAdminMenu {

    public static final String TITLE = "§8§lMarket Admin";
    private static boolean listenersChecked = false;

    private MarketAdminMenu() {}

    public static void open(Player player) {
        ensureListenersRegistered();

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
        ensureListenersRegistered();
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

    private static synchronized void ensureListenersRegistered() {
        if (listenersChecked) return;

        Nascraft plugin = Nascraft.getInstance();
        Set<Class<?>> registeredTypes = new HashSet<>();
        for (RegisteredListener registered : HandlerList.getRegisteredListeners(plugin)) {
            registeredTypes.add(registered.getListener().getClass());
        }

        registerIfMissing(plugin, registeredTypes, MarketAdminListener.class, new MarketAdminListener());
        registerIfMissing(plugin, registeredTypes, MarketEditorInvListener.class, new MarketEditorInvListener());
        registerIfMissing(plugin, registeredTypes, EditItemMenuListener.class, new EditItemMenuListener());
        registerIfMissing(plugin, registeredTypes, CategoryEditorListener.class, new CategoryEditorListener());

        listenersChecked = true;
    }

    private static void registerIfMissing(Nascraft plugin, Set<Class<?>> registeredTypes,
                                          Class<?> type, Listener listener) {
        if (registeredTypes.contains(type)) return;
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        registeredTypes.add(type);
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
