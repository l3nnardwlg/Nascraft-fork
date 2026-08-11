package me.bounser.nascraft.commands.admin.marketeditor.edit.category;

import me.bounser.nascraft.commands.admin.marketeditor.overview.MarketEditorManager;
import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.resources.Category;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CategoryEditor {

    private Category previousCategory;
    private final String identifier;
    private String displayName;
    private Material material;
    private final Player player;

    public CategoryEditor(Player player, Category category) {
        previousCategory = category;
        identifier = category.getIdentifier();
        displayName = category.getDisplayName();
        material = category.getMaterial();
        this.player = player;
        open();
    }

    public void open() {
        Inventory inventory = Bukkit.createInventory(player, 27, "§8§lEdit Category");
        insertPanes(inventory);
        insertCategoryOptions(inventory);
        player.openInventory(inventory);
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

        for (int i : new int[]{3, 4, 5, 12, 15, 22, 21, 23, 6, 7, 8, 16, 24, 25, 26}) {
            inventory.setItem(i, blackFiller);
        }
        for (int i : new int[]{0, 1, 2, 18, 19, 20}) {
            inventory.setItem(i, grayFiller);
        }
    }

    public void insertCategoryOptions(Inventory inventory) {
        inventory.setItem(10, getItemStackOfOption(
                identifier,
                Arrays.asList(
                        ChatColor.GRAY + "Display name: " + ChatColor.GOLD + displayName,
                        ChatColor.GRAY + "Material: " + ChatColor.GOLD + material.name()),
                material
        ));

        inventory.setItem(9, getItemStackOfOption(
                ChatColor.GREEN + "§lSAVE CHANGES",
                Collections.singletonList(ChatColor.GRAY + "Persist category changes"),
                Material.LIME_STAINED_GLASS_PANE
        ));

        inventory.setItem(11, getItemStackOfOption(
                ChatColor.RED + "§lCANCEL",
                Collections.singletonList(ChatColor.GRAY + "Discard unsaved changes"),
                Material.RED_STAINED_GLASS_PANE
        ));

        inventory.setItem(17, getItemStackOfOption(
                ChatColor.RED + "§lDELETE CATEGORY",
                Collections.singletonList(previousCategory.getItems().isEmpty()
                        ? ChatColor.GRAY + "Click twice to confirm"
                        : ChatColor.RED + "Move all items out before deleting"),
                Material.RED_STAINED_GLASS_PANE
        ));

        inventory.setItem(13, getItemStackOfOption(
                ChatColor.GRAY + "Category display name",
                Arrays.asList(ChatColor.GOLD + displayName, "", ChatColor.GREEN + "Click to change"),
                Material.PAPER
        ));

        inventory.setItem(14, getItemStackOfOption(
                ChatColor.GRAY + "Category material",
                Arrays.asList(ChatColor.GOLD + material.toString().toLowerCase(), "",
                        ChatColor.GREEN + "Click while holding the new material"),
                Material.PAPER
        ));
    }

    public ItemStack getItemStackOfOption(String displayName, List<String> lore, Material material) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setLore(lore);
        meta.setDisplayName(ChatColor.GOLD + displayName);
        stack.setItemMeta(meta);
        return stack;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName == null ? "" : displayName.trim();
    }

    public void setMaterial(Material material) {
        if (material != null && material != Material.AIR) this.material = material;
    }

    public void save() {
        if (displayName.isBlank()) {
            player.sendMessage(ChatColor.RED + "Category display name cannot be empty.");
            return;
        }
        if (material == null || material == Material.AIR) {
            player.sendMessage(ChatColor.RED + "Category material must be a valid item.");
            return;
        }

        List<Category> categories = MarketManager.getInstance().getCategories();
        int previousIndex = categories.indexOf(previousCategory);
        if (previousIndex < 0) {
            player.sendMessage(ChatColor.RED + "This category no longer exists. Reopen the market editor.");
            return;
        }

        Category newCategory = new Category(identifier);
        newCategory.setDisplayName(displayName);
        newCategory.setDisplayMaterial(material);
        newCategory.setItems(previousCategory.getItems());

        categories.set(previousIndex, newCategory);
        MarketManager.getInstance().setCategories(categories);

        FileConfiguration categoriesFile = Config.getInstance().getCategoriesFileConfiguration();
        categoriesFile.set("categories." + identifier + ".display-name", displayName);
        categoriesFile.set("categories." + identifier + ".display-material",
                material.equals(Material.STONE) ? null : material.toString().toLowerCase());

        try {
            categoriesFile.save(Config.getInstance().getCategoriesFile());
        } catch (IOException e) {
            categories.set(previousIndex, previousCategory);
            MarketManager.getInstance().setCategories(categories);
            player.sendMessage(ChatColor.RED + "Could not save category: " + e.getMessage());
            return;
        }

        previousCategory = newCategory;
        player.sendMessage(ChatColor.LIGHT_PURPLE + "Category changes saved.");
        if (MarketEditorManager.getInstance().getMarketEditorFromPlayer(player) != null) {
            MarketEditorManager.getInstance().getMarketEditorFromPlayer(player).open();
        }
    }

    public void removeCategory() {
        if (!previousCategory.getItems().isEmpty()) {
            player.sendMessage(ChatColor.RED + "Cannot delete a category that still contains market items.");
            open();
            return;
        }

        List<Category> categories = MarketManager.getInstance().getCategories();
        categories.remove(previousCategory);
        MarketManager.getInstance().setCategories(categories);

        FileConfiguration categoriesFile = Config.getInstance().getCategoriesFileConfiguration();
        categoriesFile.set("categories." + identifier, null);

        try {
            categoriesFile.save(Config.getInstance().getCategoriesFile());
        } catch (IOException e) {
            categories.add(previousCategory);
            MarketManager.getInstance().setCategories(categories);
            player.sendMessage(ChatColor.RED + "Could not delete category: " + e.getMessage());
            return;
        }

        player.sendMessage(ChatColor.LIGHT_PURPLE + "Category deleted.");
        MarketEditorManager.getInstance().clearEditing(player);
        MarketEditorManager.getInstance().startEditing(player);
    }
}
