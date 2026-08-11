package me.bounser.nascraft.commands.admin.marketeditor.edit.category;

import me.bounser.nascraft.commands.admin.marketeditor.overview.MarketEditor;
import me.bounser.nascraft.input.ChatInputManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Objects;

public class CategoryEditorListener implements Listener {
    private static CategoryEditorListener instance = null;
    public static CategoryEditorListener getInstance() { return instance == null ? instance = new CategoryEditorListener() : instance; }

    @EventHandler
    public void onClickInventory(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.hasPermission("nascraft.admin") && !player.hasPermission("nascraft.market.admin")) return;
        if (event.getView().getTopInventory().getSize() != 27 || !event.getView().getTitle().equals("§8§lEdit Category")) return;
        if (Objects.equals(event.getClickedInventory(), event.getView().getTopInventory())) event.setCancelled(true);
        CategoryEditor categoryEditor = CategoryEditorManager.getInstance().getEditCategoryFromPlayer(player);
        if (categoryEditor == null) { player.closeInventory(); return; }
        switch (event.getRawSlot()) {
            case 9 -> categoryEditor.save();
            case 11 -> { CategoryEditorManager.getInstance().clearEditing(player); new MarketEditor(player); }
            case 17 -> handleDelete(event, categoryEditor);
            case 13 -> openDisplayNameInput(player, categoryEditor);
            case 14 -> { ItemStack cursor = event.getCursor(); if (cursor == null || cursor.getType() == Material.AIR) { player.sendMessage(ChatColor.RED + "Put the desired material on your cursor, then click this option."); return; } categoryEditor.setMaterial(cursor.getType()); player.sendMessage(ChatColor.LIGHT_PURPLE + "Category material changed to: " + cursor.getType().name().toLowerCase()); categoryEditor.open(); }
            default -> { }
        }
    }

    private void handleDelete(InventoryClickEvent event, CategoryEditor categoryEditor) { ItemStack deletePanel = event.getCurrentItem(); if (deletePanel == null || !deletePanel.hasItemMeta()) return; ItemMeta metaDelete = deletePanel.getItemMeta(); if (!metaDelete.hasDisplayName()) return; if (metaDelete.getDisplayName().equals(ChatColor.RED + "§lDELETE CATEGORY")) { metaDelete.setDisplayName(ChatColor.DARK_RED + "§lCLICK AGAIN TO CONFIRM"); deletePanel.setItemMeta(metaDelete); event.getView().getTopInventory().setItem(17, deletePanel); } else categoryEditor.removeCategory(); }

    private void openDisplayNameInput(Player player, CategoryEditor categoryEditor) {
        ChatInputManager.getInstance().request(player, "Enter the new category display name (max 64 characters).", value -> { String name = value.trim(); if (name.isEmpty() || name.length() > 64) { player.sendMessage(ChatColor.RED + "Category name must contain 1-64 characters."); categoryEditor.open(); return; } categoryEditor.setDisplayName(name); player.sendMessage(ChatColor.LIGHT_PURPLE + "Category display name updated."); categoryEditor.open(); }, categoryEditor::open);
    }
}
