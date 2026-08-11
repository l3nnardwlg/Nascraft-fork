package me.bounser.nascraft.commands.admin.marketeditor.overview;

import de.tr7zw.changeme.nbtapi.NBT;
import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.commands.admin.marketeditor.edit.category.CategoryEditorManager;
import me.bounser.nascraft.commands.admin.marketeditor.edit.item.EditorManager;
import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.resources.Category;
import me.bounser.nascraft.market.unit.Item;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

public class MarketEditorInvListener implements Listener {

    private static MarketEditorInvListener instance = null;

    public static MarketEditorInvListener getInstance() {
        return instance == null ? instance = new MarketEditorInvListener() : instance;
    }

    @EventHandler
    public void onClickInventory(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.hasPermission("nascraft.admin") && !player.hasPermission("nascraft.market.admin")) return;

        if (event.getView().getTopInventory().getSize() != 54
                || !event.getView().getTitle().equals("§8§lAdmin view: Market")) return;

        int rawSlot = event.getRawSlot();
        if (rawSlot < event.getView().getTopInventory().getSize()) event.setCancelled(true);

        MarketEditor marketEditor = MarketEditorManager.getInstance().getMarketEditorFromPlayer(player);
        if (marketEditor == null) return;

        switch (rawSlot) {
            case 0 -> {
                if (marketEditor.canScrollUp()) {
                    marketEditor.decreaseVerticalOffset();
                    marketEditor.insertItems(event.getView().getTopInventory());
                }
            }
            case 8 -> player.closeInventory();
            case 45 -> {
                if (marketEditor.canScrollDown()) {
                    marketEditor.increaseVerticalOffset();
                    marketEditor.insertItems(event.getView().getTopInventory());
                }
            }
            case 46 -> openNewCategoryPrompt(player);
            case 48 -> {
                if (marketEditor.canScrollLeft()) {
                    marketEditor.decreaseHorizontalOffset();
                    marketEditor.insertItems(event.getView().getTopInventory());
                }
            }
            case 49 -> {
                if (event.getCursor() != null && event.getCursor().getType() != Material.AIR) {
                    startAddingItem(player, event.getCursor());
                } else {
                    player.sendMessage(ChatColor.RED + "Drop an item onto the hopper to add it to the market!");
                }
            }
            case 50 -> {
                if (marketEditor.canScrollRight()) {
                    marketEditor.increaseHorizontalOffset();
                    marketEditor.insertItems(event.getView().getTopInventory());
                }
            }
            case 7 -> toggleMarket(marketEditor, event);
            case 9, 18, 27, 36 -> {
                Category category = getCategoryFromSlot(rawSlot, player);
                if (category != null) CategoryEditorManager.getInstance().startEditing(player, category);
            }
            default -> {
                if (rawSlot < 9 || rawSlot > 44 || event.getCurrentItem() == null
                        || event.getCurrentItem().getType() == Material.AIR) return;

                Item item = getItemFromSlot(rawSlot, player);
                if (item == null) return;

                if (event.isShiftClick()) {
                    if (event.isLeftClick()) {
                        toggleAvailability(item, "buy-enabled", "Buying", player);
                    } else if (event.isRightClick()) {
                        toggleAvailability(item, "sell-enabled", "Selling", player);
                    }
                    marketEditor.insertItems(event.getView().getTopInventory());
                    return;
                }

                EditorManager.getInstance().startEditing(player, item);
            }
        }
    }

    @EventHandler
    public void onDragInventory(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.hasPermission("nascraft.admin") && !player.hasPermission("nascraft.market.admin")) return;

        if (event.getView().getTopInventory().getSize() != 54
                || !event.getView().getTitle().equals("§8§lAdmin view: Market")) return;

        if (!event.getRawSlots().contains(49)) return;

        event.setCancelled(true);
        ItemStack dragged = event.getOldCursor();
        if (dragged == null || dragged.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "Drop a valid item onto the hopper!");
            return;
        }

        startAddingItem(player, dragged);
    }

    private void startAddingItem(Player player, ItemStack source) {
        if (MarketManager.getInstance().getCategories().isEmpty()) {
            player.sendMessage(ChatColor.RED + "Create at least one category before adding market items.");
            return;
        }

        ItemStack itemStack = source.clone();
        itemStack.setAmount(1);
        for (String key : Config.getInstance().getIgnoredKeys()) {
            NBT.modify(itemStack, nbt -> {
                nbt.removeKey(key);
            });
        }
        EditorManager.getInstance().startEditing(player, itemStack);
    }

    private void openNewCategoryPrompt(Player player) {
        new AnvilGUI.Builder()
                .onClick((slot, stateSnapshot) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

                    String raw = stateSnapshot.getText().trim();
                    String identifier = raw.toLowerCase(Locale.ROOT)
                            .replace(' ', '_')
                            .replaceAll("[^a-z0-9_-]", "")
                            .replaceAll("_+", "_");

                    if (identifier.isBlank()) {
                        return Collections.singletonList(AnvilGUI.ResponseAction.replaceInputText("Identifier required"));
                    }
                    if (identifier.length() > 48) {
                        return Collections.singletonList(AnvilGUI.ResponseAction.replaceInputText("Identifier too long"));
                    }
                    if (MarketManager.getInstance().getCategoryFromIdentifier(identifier) != null) {
                        return Collections.singletonList(AnvilGUI.ResponseAction.replaceInputText("Identifier already exists"));
                    }

                    Category category = new Category(identifier);
                    category.setDisplayName(raw.isBlank() ? identifier : raw);

                    FileConfiguration categoriesFile = Config.getInstance().getCategoriesFileConfiguration();
                    categoriesFile.set("categories." + identifier + ".display-name", category.getDisplayName());
                    try {
                        categoriesFile.save(Config.getInstance().getCategoriesFile());
                    } catch (IOException e) {
                        categoriesFile.set("categories." + identifier, null);
                        stateSnapshot.getPlayer().sendMessage(ChatColor.RED + "Could not create category: " + e.getMessage());
                        return Collections.singletonList(AnvilGUI.ResponseAction.close());
                    }

                    MarketManager.getInstance().addCategory(category);
                    stateSnapshot.getPlayer().sendMessage(ChatColor.LIGHT_PURPLE + "Category created: " + category.getDisplayName());
                    return Arrays.asList(
                            AnvilGUI.ResponseAction.close(),
                            AnvilGUI.ResponseAction.run(() -> {
                                MarketEditor editor = MarketEditorManager.getInstance().getMarketEditorFromPlayer(stateSnapshot.getPlayer());
                                if (editor != null) editor.open();
                            })
                    );
                })
                .text("new_category")
                .title("New category")
                .plugin(Nascraft.getInstance())
                .open(player);
    }

    private void toggleMarket(MarketEditor marketEditor, InventoryClickEvent event) {
        if (MarketManager.getInstance().getActive()) MarketManager.getInstance().stop();
        else MarketManager.getInstance().resume();

        if (MarketManager.getInstance().getActive()) Config.getInstance().setMarketClosed();
        else Config.getInstance().setMarketOpen();

        Nascraft.getInstance().saveConfig();
        marketEditor.insertButtons(event.getView().getTopInventory());
    }

    private void toggleAvailability(Item item, String key, String label, Player player) {
        FileConfiguration items = Config.getInstance().getItemsFileConfiguration();
        String path = "items." + item.getIdentifier() + "." + key;
        boolean next = !items.getBoolean(path, true);
        items.set(path, next);
        try {
            items.save(Config.getInstance().getItemsFile());
        } catch (IOException e) {
            player.sendMessage(ChatColor.RED + "Could not save items.yml: " + e.getMessage());
            Nascraft.getInstance().getLogger().warning(
                    "Could not persist market availability for " + item.getIdentifier() + ": " + e.getMessage());
            return;
        }
        player.sendMessage(ChatColor.LIGHT_PURPLE + label + " for " + item.getName() + ": "
                + (next ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"));
    }

    public Item getItemFromSlot(int slot, Player player) {
        MarketEditor marketEditor = MarketEditorManager.getInstance().getMarketEditorFromPlayer(player);
        if (marketEditor == null) return null;

        int row = (slot / 9) - 1;
        if (row < 0 || row > 3) return null;

        int categoryIndex = marketEditor.getVerticalOffset() + row;
        if (categoryIndex >= MarketManager.getInstance().getCategories().size()) return null;

        Category category = MarketManager.getInstance().getCategories().get(categoryIndex);
        int itemIndex = (slot % 9) - 1 + marketEditor.getHorizontalOffset();
        if (itemIndex < 0 || itemIndex >= category.getNumberOfItems()) return null;
        return category.getItemOfIndex(itemIndex);
    }

    public Category getCategoryFromSlot(int slot, Player player) {
        MarketEditor marketEditor = MarketEditorManager.getInstance().getMarketEditorFromPlayer(player);
        if (marketEditor == null) return null;

        int row = (slot / 9) - 1;
        int index = marketEditor.getVerticalOffset() + row;
        if (row < 0 || row > 3 || index >= MarketManager.getInstance().getCategories().size()) return null;
        return MarketManager.getInstance().getCategories().get(index);
    }
}
