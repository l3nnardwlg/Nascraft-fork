package me.bounser.nascraft.commands.admin.marketeditor.edit.item;

import me.bounser.nascraft.commands.admin.marketeditor.overview.MarketEditorManager;
import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.managers.ImagesManager;
import me.bounser.nascraft.formatter.Formatter;
import me.bounser.nascraft.formatter.Style;
import me.bounser.nascraft.managers.currencies.CurrenciesManager;
import me.bounser.nascraft.managers.currencies.Currency;
import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.resources.Category;
import me.bounser.nascraft.market.unit.Item;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import de.tr7zw.changeme.nbtapi.NBT;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class EditItemMenu {

    private ItemStack itemStack;
    private double initialPrice;
    private String alias;
    private float elasticity;
    private float noiseIntensity;
    private double support;
    private double resistance;
    private Category prevCategory;
    private Category category;
    private Item item;
    private final Player player;
    private Currency currency;
    private boolean buyEnabled;
    private boolean sellEnabled;

    public EditItemMenu(Player player, ItemStack itemStack) {
        this.player = player;
        this.itemStack = itemStack.clone();
        this.itemStack.setAmount(1);
        initialPrice = 1;
        alias = (Character.toUpperCase(itemStack.getType().toString().toLowerCase().charAt(0)) + itemStack.getType().toString().toLowerCase().substring(1)).replace("_", " ");
        elasticity = 1;
        noiseIntensity = 1;
        support = 0;
        resistance = 0;
        buyEnabled = true;
        sellEnabled = true;
        currency = CurrenciesManager.getInstance().getDefaultCurrency();
        if (MarketManager.getInstance().getCategories().isEmpty()) {
            player.sendMessage(ChatColor.RED + "Create at least one category before adding an item.");
            return;
        }
        prevCategory = MarketManager.getInstance().getCategories().get(0);
        category = prevCategory;
        open();
    }

    public EditItemMenu(Player player, Item item) {
        this.player = player;
        this.item = item;
        itemStack = item.getItemStack().clone();
        initialPrice = item.getPrice().getInitialValue();
        alias = item.getName();
        elasticity = item.getPrice().getElasticity();
        noiseIntensity = item.getPrice().getNoiseIntensity();
        support = item.getPrice().getSupport();
        resistance = item.getPrice().getResistance();
        currency = item.getCurrency();
        prevCategory = item.getCategory();
        category = item.getCategory();
        FileConfiguration items = Config.getInstance().getItemsFileConfiguration();
        buyEnabled = items.getBoolean("items." + item.getIdentifier() + ".buy-enabled", true);
        sellEnabled = items.getBoolean("items." + item.getIdentifier() + ".sell-enabled", true);
        open();
    }

    public void open() {
        if (itemStack == null || itemStack.getType() == Material.AIR || category == null || currency == null) {
            player.sendMessage(ChatColor.RED + "Cannot open item editor because its state is incomplete.");
            return;
        }
        Inventory inventory = Bukkit.createInventory(player, 27, "§8§lEditing Item");
        insertPanes(inventory);
        insertOptions(inventory);
        insertItem(itemStack.clone(), inventory);
        this.itemStack = itemStack.clone();
        player.openInventory(inventory);
    }

    public Currency getCurrency() { return currency; }
    public Category getCategory() { return category; }
    public double getInitialPrice() { return initialPrice; }
    public String getAlias() { return alias; }
    public float getElasticity() { return elasticity; }
    public float getNoiseIntensity() { return noiseIntensity; }
    public double getSupport() { return support; }
    public double getResistance() { return resistance; }
    public boolean isBuyEnabled() { return buyEnabled; }
    public boolean isSellEnabled() { return sellEnabled; }

    public void insertItem(ItemStack itemStack, Inventory inventory) {
        ItemStack displayItemStack = itemStack.clone();
        ItemMeta meta = displayItemStack.getItemMeta();
        if (meta.hasLore()) {
            List<String> lore = meta.getLore();
            if (lore == null) lore = new java.util.ArrayList<>();
            else lore = new java.util.ArrayList<>(lore);
            lore.add("");
            lore.add(ChatColor.GREEN + "§lCLICK TO CHANGE");
            meta.setLore(lore);
        } else {
            meta.setLore(Arrays.asList("", ChatColor.GREEN + "§lCLICK TO CHANGE"));
        }
        displayItemStack.setItemMeta(meta);
        inventory.setItem(10, displayItemStack);
    }

    public void insertPanes(Inventory inventory) {
        ItemStack blackFiller = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta metaBlack = blackFiller.getItemMeta();
        metaBlack.setDisplayName(" ");
        blackFiller.setItemMeta(metaBlack);

        ItemStack grayFiller = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta metaGray = grayFiller.getItemMeta();
        metaGray.setDisplayName(" ");
        grayFiller.setItemMeta(metaGray);

        for(int i : new int[]{3, 12, 21, 7, 8, 16, 26}) inventory.setItem(i, blackFiller);
        for(int i : new int[]{0, 1, 2, 18, 19, 20}) inventory.setItem(i, grayFiller);

        ItemStack closeButton = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = closeButton.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "§lCANCEL");
        closeButton.setItemMeta(meta);
        inventory.setItem(11, closeButton);

        ItemStack confirmButton = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta metaConfirm = confirmButton.getItemMeta();
        metaConfirm.setDisplayName(ChatColor.GREEN + "§lSAVE CHANGES");
        confirmButton.setItemMeta(metaConfirm);
        inventory.setItem(9, confirmButton);

        ItemStack deletePanel = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta metaDelete = deletePanel.getItemMeta();
        metaDelete.setDisplayName(ChatColor.RED + "§lDELETE ITEM");
        if (item == null) metaDelete.setLore(List.of(ChatColor.GRAY + "Unsaved item: cancel instead of delete."));
        deletePanel.setItemMeta(metaDelete);
        inventory.setItem(17, deletePanel);
    }

    public void insertOptions(Inventory inventory) {
        Component priceComponent = MiniMessage.miniMessage().deserialize(Formatter.format(currency, initialPrice, Style.ROUND_BASIC));
        inventory.setItem(4, getItemStackOfOption(Material.GOLD_INGOT,
                "Initial Price " + ChatColor.UNDERLINE + "(REQUIRED)",
                Arrays.asList(ChatColor.GRAY + "Value: " + LegacyComponentSerializer.legacySection().serialize(priceComponent),
                        "", ChatColor.GRAY + "Must be greater than zero.", "",
                        ChatColor.GREEN + "" + ChatColor.BOLD + "CLICK TO EDIT")));

        inventory.setItem(5, getItemStackOfOption(Material.NAME_TAG, "Alias",
                Arrays.asList(ChatColor.GRAY + "Alias: " + ChatColor.GREEN + alias, "",
                        ChatColor.GREEN + "" + ChatColor.BOLD + "CLICK TO EDIT")));

        inventory.setItem(6, getItemStackOfOption(Material.GOLD_NUGGET, "Currency",
                Arrays.asList(ChatColor.GRAY + "Currency: " + ChatColor.GREEN + currency.getCurrencyIdentifier(), "",
                        ChatColor.GREEN + "" + ChatColor.BOLD + "CLICK TO SWITCH")));

        inventory.setItem(13, getItemStackOfOption(Material.SLIME_BALL, "Elasticity",
                Arrays.asList(ChatColor.GRAY + "Value: " + ChatColor.GREEN + elasticity, "",
                        ChatColor.GREEN + "" + ChatColor.BOLD + "CLICK TO EDIT")));

        inventory.setItem(14, getItemStackOfOption(Material.COMPARATOR, "Noise Intensity",
                Arrays.asList(ChatColor.GRAY + "Value: " + ChatColor.GREEN + noiseIntensity, "",
                        ChatColor.GREEN + "" + ChatColor.BOLD + "CLICK TO EDIT")));

        Component supportComponent = MiniMessage.miniMessage().deserialize(Formatter.format(currency, support, Style.ROUND_BASIC));
        inventory.setItem(22, getItemStackOfOption(Material.BEDROCK, "Support",
                Arrays.asList(ChatColor.GRAY + "Value: " + (support == 0 ? ChatColor.RED + "DISABLED" : LegacyComponentSerializer.legacySection().serialize(supportComponent)), "",
                        ChatColor.GREEN + "" + ChatColor.BOLD + "CLICK TO EDIT")));

        Component resistanceComponent = MiniMessage.miniMessage().deserialize(Formatter.format(currency, resistance, Style.ROUND_BASIC));
        inventory.setItem(23, getItemStackOfOption(Material.WHITE_WOOL, "Resistance",
                Arrays.asList(ChatColor.GRAY + "Value: " + (resistance == 0 ? ChatColor.RED + "DISABLED" : LegacyComponentSerializer.legacySection().serialize(resistanceComponent)), "",
                        ChatColor.GREEN + "" + ChatColor.BOLD + "CLICK TO EDIT")));

        inventory.setItem(15, getItemStackOfOption(Material.CHEST, "Category",
                Arrays.asList(ChatColor.GRAY + "Category: " + ChatColor.GREEN + category.getIdentifier() + ChatColor.GRAY + " - " + ChatColor.GOLD + category.getDisplayName(),
                        "", ChatColor.GREEN + "Left click: next category", ChatColor.YELLOW + "Right click: previous category")));

        inventory.setItem(24, getItemStackOfOption(buyEnabled ? Material.LIME_DYE : Material.GRAY_DYE, "Buying",
                Arrays.asList(ChatColor.GRAY + "Status: " + (buyEnabled ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"), "",
                        ChatColor.GREEN + "Click to toggle")));
        inventory.setItem(25, getItemStackOfOption(sellEnabled ? Material.LIME_DYE : Material.GRAY_DYE, "Selling",
                Arrays.asList(ChatColor.GRAY + "Status: " + (sellEnabled ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"), "",
                        ChatColor.GREEN + "Click to toggle")));
    }

    public static ItemStack getItemStackOfOption(Material material, String displayName, List<String> value) {
        ItemStack paper = new ItemStack(material);
        ItemMeta meta = paper.getItemMeta();
        meta.setLore(value);
        meta.setDisplayName(ChatColor.GOLD + displayName);
        paper.setItemMeta(meta);
        return paper;
    }

    public void setItemStack(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) return;
        this.itemStack = itemStack.clone();
        this.itemStack.setAmount(1);
    }
    public void setInitialPrice(float initialPrice) { this.initialPrice = initialPrice; }
    public void setAlias(String alias) { this.alias = alias; }
    public void setElasticity(float elasticity) { this.elasticity = elasticity; }
    public void setNoiseIntensity(float noiseIntensity) { this.noiseIntensity = noiseIntensity; }
    public void setSupport(float support) { this.support = support; }
    public void setResistance(float resistance) { this.resistance = resistance; }
    public void setCategory(Category category) { if (category != null) this.category = category; }
    public void setCurrency(Currency currency) { if (currency != null) this.currency = currency; }
    public void toggleBuyEnabled() { buyEnabled = !buyEnabled; }
    public void toggleSellEnabled() { sellEnabled = !sellEnabled; }

    public boolean save() {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "Select a valid item before saving.");
            return false;
        }
        if (alias == null || alias.trim().isEmpty()) {
            player.sendMessage(ChatColor.RED + "Alias cannot be empty.");
            return false;
        }
        if (!Double.isFinite(initialPrice) || initialPrice <= 0) {
            player.sendMessage(ChatColor.RED + "Initial price must be greater than zero.");
            return false;
        }
        if (!Float.isFinite(elasticity) || elasticity < 0 || !Float.isFinite(noiseIntensity) || noiseIntensity < 0
                || !Double.isFinite(support) || support < 0 || !Double.isFinite(resistance) || resistance < 0) {
            player.sendMessage(ChatColor.RED + "One or more market values are invalid.");
            return false;
        }
        if (category == null || currency == null) {
            player.sendMessage(ChatColor.RED + "Category and currency are required.");
            return false;
        }

        FileConfiguration items = Config.getInstance().getItemsFileConfiguration();
        String identifier;

        if (item == null) {
            int count = 0;
            for (Item existing : MarketManager.getInstance().getAllItems())
                if (existing.getItemStack().getType().equals(itemStack.getType())) count++;
            identifier = count == 0 ? itemStack.getType().toString().toLowerCase() : itemStack.getType().toString().toLowerCase() + count;
        } else identifier = item.getIdentifier();

        items.set("items." + identifier + ".alias", alias.trim());
        items.set("items." + identifier + ".initial-price", initialPrice);
        items.set("items." + identifier + ".elasticity", elasticity);
        items.set("items." + identifier + ".noise-intensity", noiseIntensity);
        items.set("items." + identifier + ".support", support == 0 ? null : support);
        items.set("items." + identifier + ".resistance", resistance == 0 ? null : resistance);
        items.set("items." + identifier + ".buy-enabled", buyEnabled);
        items.set("items." + identifier + ".sell-enabled", sellEnabled);
        items.set("items." + identifier + ".item-stack", NBT.itemStackToNBT(itemStack).toString());
        items.set("items." + identifier + ".currency",
                currency.equals(CurrenciesManager.getInstance().getDefaultCurrency()) ? null : currency.getCurrencyIdentifier());

        FileConfiguration categories = Config.getInstance().getCategoriesFileConfiguration();
        List<String> itemsOfPrevCategory = categories.getStringList("categories." + prevCategory.getIdentifier() + ".items");
        itemsOfPrevCategory.remove(identifier);
        categories.set("categories." + prevCategory.getIdentifier() + ".items", itemsOfPrevCategory);

        List<String> itemsOfNewCategory = categories.getStringList("categories." + category.getIdentifier() + ".items");
        if (!itemsOfNewCategory.contains(identifier)) itemsOfNewCategory.add(identifier);
        categories.set("categories." + category.getIdentifier() + ".items", itemsOfNewCategory);

        try {
            categories.save(Config.getInstance().getCategoriesFile());
            items.save(Config.getInstance().getItemsFile());
        } catch (IOException e) {
            player.sendMessage(ChatColor.RED + "Could not save market configuration: " + e.getMessage());
            return false;
        }

        if (item != null) {
            item.setItemStack(itemStack.clone());
            item.setCategory(category);
            item.setCurrency(currency);
            item.changeProperties(initialPrice, alias.trim(), elasticity, noiseIntensity, support, resistance);
            if (!prevCategory.getIdentifier().equals(category.getIdentifier())) {
                category.addItem(item);
                prevCategory.removeItem(item);
                prevCategory = category;
            }
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Item changes saved successfully.");
        } else {
            item = new Item(itemStack.clone(), identifier, alias.trim(), category, ImagesManager.getInstance().getImage(identifier));
            item.setCurrency(currency);
            category.addItem(item);
            MarketManager.getInstance().addItem(item);
            prevCategory = category;
            player.sendMessage(ChatColor.LIGHT_PURPLE + "New market item saved successfully.");
        }

        EditorManager.getInstance().clearEditing(player);
        return true;
    }

    public void removeItem() {
        if (item == null) {
            player.sendMessage(ChatColor.RED + "This item has not been saved yet. Use Cancel instead.");
            return;
        }

        MarketManager.getInstance().removeItem(item);
        prevCategory.removeItem(item);
        FileConfiguration items = Config.getInstance().getItemsFileConfiguration();
        items.set("items." + item.getIdentifier(), null);
        FileConfiguration categories = Config.getInstance().getCategoriesFileConfiguration();
        List<String> categoryItems = categories.getStringList("categories." + prevCategory.getIdentifier() + ".items");
        categoryItems.remove(item.getIdentifier());
        categories.set("categories." + prevCategory.getIdentifier() + ".items", categoryItems);
        try {
            items.save(Config.getInstance().getItemsFile());
            categories.save(Config.getInstance().getCategoriesFile());
        } catch (IOException e) {
            player.sendMessage(ChatColor.RED + "Could not delete market item: " + e.getMessage());
            return;
        }
        EditorManager.getInstance().clearEditing(player);
        if (MarketEditorManager.getInstance().getMarketEditorFromPlayer(player) != null)
            MarketEditorManager.getInstance().getMarketEditorFromPlayer(player).open();
    }
}
