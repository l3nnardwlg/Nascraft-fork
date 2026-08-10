package me.bounser.nascraft.commands.admin.marketeditor.edit.item;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.commands.admin.marketeditor.overview.MarketEditorManager;
import me.bounser.nascraft.managers.currencies.CurrenciesManager;
import me.bounser.nascraft.managers.currencies.Currency;
import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.resources.Category;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class EditItemMenuListener implements Listener {

    @EventHandler
    public void onClickInventory(InventoryClickEvent event) {

        if (!event.getWhoClicked().hasPermission("nascraft.admin")
                && !event.getWhoClicked().hasPermission("nascraft.market.admin")) return;

        if (event.getView().getTopInventory().getSize() != 27
                || !event.getView().getTitle().equals("§8§lEditing Item")) return;

        Player player = (Player) event.getWhoClicked();

        if (Objects.equals(event.getClickedInventory(), event.getView().getTopInventory())) event.setCancelled(true);

        EditItemMenu editor = EditorManager.getInstance().getEditItemMenuFromPlayer(player);
        if (editor == null) return;

        switch (event.getRawSlot()) {
            case 11:
                EditorManager.getInstance().clearEditing(player);
                if (MarketEditorManager.getInstance().getMarketEditorFromPlayer(player) != null) {
                    MarketEditorManager.getInstance().getMarketEditorFromPlayer(player).open();
                }
                break;

            case 9:
                editor.save();
                break;

            case 10:
                ItemStack newItem = event.getCursor();
                if (newItem == null || newItem.getType() == Material.AIR || newItem.getAmount() == 0) {
                    player.sendMessage(ChatColor.RED + "Invalid item!");
                    return;
                }

                ItemStack replacement = newItem.clone();
                replacement.setAmount(1);
                editor.setItemStack(replacement);
                editor.open();
                break;

            case 17:
                ItemStack deletePanel = event.getCurrentItem();
                if (deletePanel == null) return;

                ItemMeta metaDelete = deletePanel.getItemMeta();
                if (metaDelete.getDisplayName().equals(ChatColor.RED + "§lDELETE ITEM")) {
                    metaDelete.setDisplayName(ChatColor.RED + "§lCONFIRM");
                    deletePanel.setItemMeta(metaDelete);
                } else {
                    editor.removeItem();
                    player.sendMessage(ChatColor.LIGHT_PURPLE + "Item deleted.");
                }
                break;

            case 4:
                openAnvil(player,
                        ChatColor.LIGHT_PURPLE + "Initial price set correctly!",
                        "Initial price...",
                        "Initial price",
                        "initialprice");
                break;

            case 5:
                new AnvilGUI.Builder()
                        .onClick((slot, stateSnapshot) -> {
                            if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();
                            editor.setAlias(stateSnapshot.getText());
                            stateSnapshot.getPlayer().sendMessage(ChatColor.LIGHT_PURPLE + "Alias set correctly!");
                            return Arrays.asList(
                                    AnvilGUI.ResponseAction.close(),
                                    AnvilGUI.ResponseAction.run(editor::open)
                            );
                        })
                        .preventClose()
                        .text("Item Alias...")
                        .title("Item Alias")
                        .plugin(Nascraft.getInstance())
                        .open(player);
                break;

            case 6:
                List<Currency> currencies = CurrenciesManager.getInstance().getCurrencies();
                if (currencies.isEmpty()) return;

                int index = currencies.indexOf(editor.getCurrency());
                Currency currency = currencies.get(index < 0 || index + 1 >= currencies.size() ? 0 : index + 1);
                editor.setCurrency(currency);
                editor.insertOptions(event.getInventory());
                break;

            case 13:
                openAnvil(player,
                        ChatColor.LIGHT_PURPLE + "Elasticity set correctly!",
                        "Price Elasticity...",
                        "Price Elasticity",
                        "elasticity");
                break;

            case 14:
                openAnvil(player,
                        ChatColor.LIGHT_PURPLE + "Noise intensity set correctly!",
                        "Noise intensity...",
                        "Noise intensity",
                        "noiseintensity");
                break;

            case 22:
                openAnvil(player,
                        ChatColor.LIGHT_PURPLE + "Support set correctly!",
                        "Price Support...",
                        "Price Support",
                        "support");
                break;

            case 23:
                openAnvil(player,
                        ChatColor.LIGHT_PURPLE + "Resistance set correctly!",
                        "Price Resistance...",
                        "Price Resistance",
                        "resistance");
                break;

            case 15:
                cycleCategory(player, editor, event.isRightClick() ? -1 : 1);
                break;
        }
    }

    private void cycleCategory(Player player, EditItemMenu editor, int direction) {
        List<Category> categories = MarketManager.getInstance().getCategories();
        if (categories.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No categories exist. Create a category in the market editor first.");
            return;
        }

        Category current = editor.getCategory();
        int index = categories.indexOf(current);
        if (index < 0) index = 0;

        int nextIndex = Math.floorMod(index + direction, categories.size());
        Category selected = categories.get(nextIndex);
        editor.setCategory(selected);
        player.sendMessage(ChatColor.LIGHT_PURPLE + "Category: " + ChatColor.GOLD + selected.getDisplayName());
        editor.open();
    }

    public void openAnvil(Player player, String setupedCorrectly, String text, String title, String type) {
        new AnvilGUI.Builder()
                .onClick((slot, stateSnapshot) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

                    try {
                        float value = Float.parseFloat(stateSnapshot.getText());
                        if (value < 0) {
                            return Arrays.asList(AnvilGUI.ResponseAction.replaceInputText("Cannot be negative!"));
                        }

                        EditItemMenu editor = EditorManager.getInstance().getEditItemMenuFromPlayer(player);
                        if (editor == null) return Collections.emptyList();

                        stateSnapshot.getPlayer().sendMessage(setupedCorrectly);
                        switch (type) {
                            case "initialprice" -> editor.setInitialPrice(value);
                            case "elasticity" -> editor.setElasticity(value);
                            case "noiseintensity" -> editor.setNoiseIntensity(value);
                            case "support" -> editor.setSupport(value);
                            case "resistance" -> editor.setResistance(value);
                        }

                        return Arrays.asList(
                                AnvilGUI.ResponseAction.close(),
                                AnvilGUI.ResponseAction.run(editor::open)
                        );
                    } catch (NumberFormatException e) {
                        return Arrays.asList(AnvilGUI.ResponseAction.replaceInputText("Not a valid format!"));
                    }
                })
                .preventClose()
                .text(text)
                .title(title)
                .plugin(Nascraft.getInstance())
                .open(player);
    }
}
