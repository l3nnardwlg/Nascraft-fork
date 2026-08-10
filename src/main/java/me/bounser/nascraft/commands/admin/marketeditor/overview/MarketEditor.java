package me.bounser.nascraft.commands.admin.marketeditor.overview;

import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.formatter.Formatter;
import me.bounser.nascraft.formatter.Style;
import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.resources.Category;
import me.bounser.nascraft.market.unit.Item;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MarketEditor {

    private int verticalOffset, horizontalOffset;
    private final Player player;

    public MarketEditor(Player player) {
        this.player = player;
        verticalOffset = 0;
        horizontalOffset = 0;
        open();
    }

    public void open() {
        Inventory inventory = Bukkit.createInventory(player, 54, "§8§lAdmin view: Market");
        insertFillingPanes(inventory);
        insertNavigation(inventory);
        insertHelpHead(inventory);
        insertButtons(inventory);
        insertItems(inventory);
        player.openInventory(inventory);
    }

    public void insertFillingPanes(Inventory inventory) {
        ItemStack blackFiller = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta metaBlack = blackFiller.getItemMeta();
        metaBlack.setDisplayName(" ");
        blackFiller.setItemMeta(metaBlack);

        for (int i : new int[]{1, 2, 3, 5, 6, 47, 51, 52, 53}) {
            inventory.setItem(i, blackFiller);
        }

        ItemStack closeButton = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = closeButton.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "§lCLOSE");
        closeButton.setItemMeta(meta);
        inventory.setItem(8, closeButton);
    }

    public void insertNavigation(Inventory inventory) {
        inventory.setItem(0, navigationItem(
                canScrollUp() ? Material.FIREWORK_ROCKET : Material.BARRIER,
                canScrollUp() ? ChatColor.LIGHT_PURPLE + "§lSCROLL UP" : ChatColor.RED + "§lTOP REACHED"
        ));

        inventory.setItem(45, navigationItem(
                canScrollDown() ? Material.ANVIL : Material.BARRIER,
                canScrollDown() ? ChatColor.LIGHT_PURPLE + "§lSCROLL DOWN" : ChatColor.RED + "§lBOTTOM REACHED"
        ));

        inventory.setItem(48, navigationItem(
                canScrollLeft() ? Material.ARROW : Material.BARRIER,
                canScrollLeft() ? ChatColor.LIGHT_PURPLE + "§l< LEFT" : ChatColor.RED + "§lLEFT EDGE"
        ));

        inventory.setItem(50, navigationItem(
                canScrollRight() ? Material.ARROW : Material.BARRIER,
                canScrollRight() ? ChatColor.LIGHT_PURPLE + "§lRIGHT >" : ChatColor.RED + "§lRIGHT EDGE"
        ));
    }

    private ItemStack navigationItem(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name);
        stack.setItemMeta(meta);
        return stack;
    }

    public void insertHelpHead(Inventory inventory) {
        ItemStack info = new ItemStack(Material.CHEST);
        ItemMeta meta = info.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_PURPLE + "§lMARKET EDITOR");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Add, remove and configure market items.",
                ChatColor.GRAY + "Drop an item directly onto the hopper to add it.",
                "",
                ChatColor.YELLOW + "Shift + Left Click: toggle buying",
                ChatColor.YELLOW + "Shift + Right Click: toggle selling"
        ));
        info.setItemMeta(meta);
        inventory.setItem(4, info);
    }

    public void insertButtons(Inventory inventory) {
        ItemStack newItem = new ItemStack(Material.HOPPER);
        ItemMeta metaNewItem = newItem.getItemMeta();
        metaNewItem.setDisplayName(ChatColor.BLUE + "§lADD ITEM TO MARKET");
        metaNewItem.setLore(Arrays.asList(
                ChatColor.GRAY + "Drop an item here to configure it",
                ChatColor.GRAY + "as a new market item."
        ));
        newItem.setItemMeta(metaNewItem);
        inventory.setItem(49, newItem);

        ItemStack newCategory = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta newCategoryItemMeta = newCategory.getItemMeta();
        newCategoryItemMeta.setDisplayName(ChatColor.BLUE + "§lNEW CATEGORY");
        newCategoryItemMeta.setLore(Arrays.asList(ChatColor.GRAY + "Click to create a new category."));
        newCategory.setItemMeta(newCategoryItemMeta);
        inventory.setItem(46, newCategory);

        ItemStack enabled;
        ItemMeta metaEnabled;

        if (MarketManager.getInstance().getActive()) {
            enabled = new ItemStack(Material.LIME_DYE);
            metaEnabled = enabled.getItemMeta();
            metaEnabled.setDisplayName(ChatColor.GREEN + "§lMARKET ACTIVE");
            metaEnabled.setLore(Arrays.asList(
                    ChatColor.GRAY + "Click to stop the market.",
                    ChatColor.GRAY + "Users won't be able to buy/sell."
            ));
        } else {
            enabled = new ItemStack(Material.RED_DYE);
            metaEnabled = enabled.getItemMeta();
            metaEnabled.setDisplayName(ChatColor.RED + "§lMARKET STOPPED");
            metaEnabled.setLore(Arrays.asList(
                    ChatColor.GRAY + "Click to resume the market.",
                    ChatColor.GRAY + "Users will be able to buy/sell."
            ));
        }

        enabled.setItemMeta(metaEnabled);
        inventory.setItem(7, enabled);
    }

    public void insertItems(Inventory inventory) {
        for (int slot = 9; slot <= 44; slot++) inventory.clear(slot);

        List<Category> categories = new ArrayList<>();
        List<Category> allCategories = MarketManager.getInstance().getCategories();

        for (int i = 0; i <= 3; i++) {
            if (allCategories.size() > i + verticalOffset) {
                categories.add(allCategories.get(i + verticalOffset));
            }
        }

        int row = 0;
        for (Category category : categories) {
            ItemStack categoryItemStack = new ItemStack(category.getMaterial());
            ItemMeta categoryMeta = categoryItemStack.getItemMeta();
            categoryMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "Category: " + category.getDisplayName());
            categoryMeta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Identifier: " + ChatColor.GOLD + category.getIdentifier(),
                    "",
                    ChatColor.GREEN + "§lCLICK TO EDIT"
            ));
            categoryItemStack.setItemMeta(categoryMeta);
            inventory.setItem(9 + 9 * row, categoryItemStack);

            List<Item> items = new ArrayList<>();
            if (horizontalOffset < category.getNumberOfItems()) {
                items = new ArrayList<>(category.getItems().subList(horizontalOffset, category.getNumberOfItems()));
            }
            while (items.size() < 8) items.add(null);

            for (int column = 1; column <= 8; column++) {
                Item item = items.get(column - 1);
                if (item == null) continue;

                ItemStack itemStack = item.getItemStack().clone();
                ItemMeta meta = itemStack.getItemMeta();
                meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Alias: " + item.getName());

                Component price = MiniMessage.miniMessage().deserialize(Formatter.format(item.getCurrency(), item.getPrice().getInitialValue(), Style.ROUND_BASIC));
                Component support = MiniMessage.miniMessage().deserialize(Formatter.format(item.getCurrency(), item.getPrice().getSupport(), Style.ROUND_BASIC));
                Component resistance = MiniMessage.miniMessage().deserialize(Formatter.format(item.getCurrency(), item.getPrice().getResistance(), Style.ROUND_BASIC));

                boolean buyEnabled = Config.getInstance().getItemsFileConfiguration()
                        .getBoolean("items." + item.getIdentifier() + ".buy-enabled", true);
                boolean sellEnabled = Config.getInstance().getItemsFileConfiguration()
                        .getBoolean("items." + item.getIdentifier() + ".sell-enabled", true);

                meta.setLore(Arrays.asList(
                        ChatColor.GRAY + "Initial price: " + LegacyComponentSerializer.legacySection().serialize(price),
                        ChatColor.GRAY + "Elasticity: " + ChatColor.GREEN + item.getPrice().getElasticity(),
                        ChatColor.GRAY + "Noise Intensity: " + ChatColor.GREEN + item.getPrice().getNoiseIntensity(),
                        ChatColor.GRAY + "Support: " + (item.getPrice().getSupport() == 0 ? ChatColor.RED + "DISABLED" : LegacyComponentSerializer.legacySection().serialize(support)),
                        ChatColor.GRAY + "Resistance: " + (item.getPrice().getResistance() == 0 ? ChatColor.RED + "DISABLED" : LegacyComponentSerializer.legacySection().serialize(resistance)),
                        "",
                        ChatColor.GRAY + "Buying: " + (buyEnabled ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"),
                        ChatColor.GRAY + "Selling: " + (sellEnabled ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"),
                        "",
                        ChatColor.GREEN + "§lCLICK TO EDIT",
                        ChatColor.YELLOW + "Shift + Left: toggle buying",
                        ChatColor.YELLOW + "Shift + Right: toggle selling"
                ));

                itemStack.setItemMeta(meta);
                inventory.setItem(((row + 1) * 9) + column, itemStack);
            }
            row++;
        }

        insertNavigation(inventory);
    }

    public boolean canScrollUp() {
        return verticalOffset > 0;
    }

    public boolean canScrollDown() {
        return verticalOffset + 4 < MarketManager.getInstance().getCategories().size();
    }

    public boolean canScrollLeft() {
        return horizontalOffset > 0;
    }

    public boolean canScrollRight() {
        int biggestCategory = 0;
        for (Category category : MarketManager.getInstance().getCategories()) {
            biggestCategory = Math.max(biggestCategory, category.getNumberOfItems());
        }
        return horizontalOffset + 8 < biggestCategory;
    }

    public void increaseVerticalOffset() {
        if (canScrollDown()) verticalOffset++;
    }

    public void increaseHorizontalOffset() {
        if (canScrollRight()) horizontalOffset++;
    }

    public void decreaseVerticalOffset() {
        if (canScrollUp()) verticalOffset--;
    }

    public void decreaseHorizontalOffset() {
        if (canScrollLeft()) horizontalOffset--;
    }

    public int getVerticalOffset() { return verticalOffset; }
    public int getHorizontalOffset() { return horizontalOffset; }
}
