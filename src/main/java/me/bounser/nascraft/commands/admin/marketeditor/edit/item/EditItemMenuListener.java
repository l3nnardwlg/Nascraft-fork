package me.bounser.nascraft.commands.admin.marketeditor.edit.item;

import me.bounser.nascraft.commands.admin.marketeditor.overview.MarketEditorManager;
import me.bounser.nascraft.input.ChatInputManager;
import me.bounser.nascraft.managers.currencies.CurrenciesManager;
import me.bounser.nascraft.managers.currencies.Currency;
import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.resources.Category;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

public class EditItemMenuListener implements Listener {
    private static final String SELECTOR_TITLE = "§8§lSelect Market Item";
    private static final int PAGE_SIZE = 45;
    private final Map<UUID, SelectorState> selectors = new HashMap<>();

    @EventHandler
    public void onClickInventory(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.hasPermission("nascraft.admin") && !player.hasPermission("nascraft.market.admin")) return;
        if (event.getView().getTitle().equals(SELECTOR_TITLE)) { handleSelectorClick(event, player); return; }
        if (event.getView().getTopInventory().getSize() != 27 || !event.getView().getTitle().equals("§8§lEditing Item")) return;
        event.setCancelled(true);
        EditItemMenu editor = EditorManager.getInstance().getEditItemMenuFromPlayer(player);
        if (editor == null) { player.closeInventory(); return; }
        switch (event.getRawSlot()) {
            case 11 -> { EditorManager.getInstance().clearEditing(player); if (MarketEditorManager.getInstance().getMarketEditorFromPlayer(player) != null) MarketEditorManager.getInstance().getMarketEditorFromPlayer(player).open(); }
            case 9 -> editor.save();
            case 10 -> openItemSelector(player, editor, "", 0);
            case 17 -> handleDelete(event, player, editor);
            case 4 -> openNumberInput(player, editor, "Initial price", editor.getInitialPrice(), "initialprice");
            case 5 -> openAliasInput(player, editor);
            case 6 -> cycleCurrency(editor);
            case 13 -> openNumberInput(player, editor, "Price elasticity", editor.getElasticity(), "elasticity");
            case 14 -> openNumberInput(player, editor, "Noise intensity", editor.getNoiseIntensity(), "noiseintensity");
            case 22 -> openNumberInput(player, editor, "Price support", editor.getSupport(), "support");
            case 23 -> openNumberInput(player, editor, "Price resistance", editor.getResistance(), "resistance");
            case 15 -> cycleCategory(player, editor, event.isRightClick() ? -1 : 1);
            case 24 -> { editor.toggleBuyEnabled(); editor.open(); }
            case 25 -> { editor.toggleSellEnabled(); editor.open(); }
            default -> { }
        }
    }

    @EventHandler public void onClose(InventoryCloseEvent event) { if (event.getView().getTitle().equals(SELECTOR_TITLE)) selectors.remove(event.getPlayer().getUniqueId()); }

    private void handleDelete(InventoryClickEvent event, Player player, EditItemMenu editor) {
        ItemStack deletePanel = event.getCurrentItem(); if (deletePanel == null || !deletePanel.hasItemMeta()) return;
        ItemMeta meta = deletePanel.getItemMeta(); if (!meta.hasDisplayName()) return;
        if (meta.getDisplayName().equals(ChatColor.RED + "§lDELETE ITEM")) { meta.setDisplayName(ChatColor.DARK_RED + "§lCLICK AGAIN TO CONFIRM"); deletePanel.setItemMeta(meta); event.getInventory().setItem(17, deletePanel); }
        else { editor.removeItem(); player.sendMessage(ChatColor.LIGHT_PURPLE + "Item deleted."); }
    }

    private void openAliasInput(Player player, EditItemMenu editor) {
        ChatInputManager.getInstance().request(player, "Enter the new alias (max 64 characters). Current: " + editor.getAlias(), value -> {
            String alias = value.trim();
            if (alias.isEmpty() || alias.length() > 64) { player.sendMessage(ChatColor.RED + "Alias must contain 1-64 characters."); editor.open(); return; }
            editor.setAlias(alias); player.sendMessage(ChatColor.LIGHT_PURPLE + "Alias updated."); editor.open();
        }, editor::open);
    }

    private void openNumberInput(Player player, EditItemMenu editor, String title, double currentValue, String type) {
        ChatInputManager.getInstance().request(player, "Enter " + title.toLowerCase(Locale.ROOT) + ". Current: " + currentValue, raw -> {
            try {
                double value = Double.parseDouble(raw.trim().replace(',', '.'));
                boolean invalid = !Double.isFinite(value) || value < 0 || (type.equals("initialprice") && value <= 0);
                if (invalid || (value > Float.MAX_VALUE && !type.equals("support") && !type.equals("resistance"))) { player.sendMessage(ChatColor.RED + "Invalid value."); editor.open(); return; }
                switch (type) {
                    case "initialprice" -> editor.setInitialPrice((float) value);
                    case "elasticity" -> editor.setElasticity((float) value);
                    case "noiseintensity" -> editor.setNoiseIntensity((float) value);
                    case "support" -> editor.setSupport((float) value);
                    case "resistance" -> editor.setResistance((float) value);
                    default -> { player.sendMessage(ChatColor.RED + "Unknown editor field."); editor.open(); return; }
                }
                player.sendMessage(ChatColor.LIGHT_PURPLE + title + " set to " + value + "."); editor.open();
            } catch (NumberFormatException ex) { player.sendMessage(ChatColor.RED + "Invalid number."); editor.open(); }
        }, editor::open);
    }

    private void cycleCurrency(EditItemMenu editor) { List<Currency> currencies = CurrenciesManager.getInstance().getCurrencies(); if (currencies.isEmpty()) return; int index = currencies.indexOf(editor.getCurrency()); editor.setCurrency(currencies.get(index < 0 || index + 1 >= currencies.size() ? 0 : index + 1)); editor.open(); }
    private void cycleCategory(Player player, EditItemMenu editor, int direction) { List<Category> categories = MarketManager.getInstance().getCategories(); if (categories.isEmpty()) { player.sendMessage(ChatColor.RED + "No categories exist. Create a category first."); return; } int index = categories.indexOf(editor.getCategory()); if (index < 0) index = 0; editor.setCategory(categories.get(Math.floorMod(index + direction, categories.size()))); editor.open(); }

    private void openItemSelector(Player player, EditItemMenu editor, String query, int page) {
        List<Material> materials = getSelectableMaterials(query); int maxPage = Math.max(0, (materials.size() - 1) / PAGE_SIZE); page = Math.max(0, Math.min(page, maxPage)); selectors.put(player.getUniqueId(), new SelectorState(query, page));
        Inventory inventory = Bukkit.createInventory(player, 54, SELECTOR_TITLE); int start = page * PAGE_SIZE;
        for (int slot = 0; slot < PAGE_SIZE && start + slot < materials.size(); slot++) { Material material = materials.get(start + slot); ItemStack stack = new ItemStack(material); ItemMeta meta = stack.getItemMeta(); meta.setDisplayName(ChatColor.GOLD + prettify(material.name())); meta.setLore(List.of(ChatColor.GRAY + material.getKey().toString(), "", ChatColor.GREEN + "Click to select")); stack.setItemMeta(meta); inventory.setItem(slot, stack); }
        inventory.setItem(45, navigationItem(page > 0 ? Material.ARROW : Material.BARRIER, "Previous page")); inventory.setItem(48, navigationItem(Material.BARRIER, "Back")); inventory.setItem(49, navigationItem(Material.OAK_SIGN, query.isBlank() ? "Search items" : "Search: " + query)); inventory.setItem(53, navigationItem(page < maxPage ? Material.ARROW : Material.BARRIER, "Next page")); player.openInventory(inventory);
    }

    private void handleSelectorClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true); EditItemMenu editor = EditorManager.getInstance().getEditItemMenuFromPlayer(player); if (editor == null) { player.closeInventory(); return; }
        SelectorState state = selectors.getOrDefault(player.getUniqueId(), new SelectorState("", 0)); int slot = event.getRawSlot();
        if (slot >= 0 && slot < PAGE_SIZE) { ItemStack clicked = event.getCurrentItem(); if (clicked == null || clicked.getType() == Material.AIR) return; editor.setItemStack(new ItemStack(clicked.getType())); selectors.remove(player.getUniqueId()); editor.open(); return; }
        if (slot == 45 && state.page() > 0) openItemSelector(player, editor, state.query(), state.page() - 1); else if (slot == 53) openItemSelector(player, editor, state.query(), state.page() + 1); else if (slot == 48) { selectors.remove(player.getUniqueId()); editor.open(); } else if (slot == 49) openSearchInput(player, editor, state.query());
    }

    private void openSearchInput(Player player, EditItemMenu editor, String currentQuery) {
        ChatInputManager.getInstance().request(player, "Enter an item material to filter the selector.", raw -> openItemSelector(player, editor, raw.trim().toLowerCase(Locale.ROOT), 0), editor::open);
    }

    private List<Material> getSelectableMaterials(String query) { String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT).replace(' ', '_'); return Arrays.stream(Material.values()).filter(Material::isItem).filter(material -> material != Material.AIR).filter(material -> normalized.isBlank() || material.name().toLowerCase(Locale.ROOT).contains(normalized)).sorted(Comparator.comparing(Enum::name)).collect(Collectors.toList()); }
    private ItemStack navigationItem(Material material, String name) { ItemStack stack = new ItemStack(material); ItemMeta meta = stack.getItemMeta(); meta.setDisplayName(ChatColor.YELLOW + name); stack.setItemMeta(meta); return stack; }
    private String prettify(String name) { String[] parts = name.toLowerCase(Locale.ROOT).split("_"); StringBuilder builder = new StringBuilder(); for (String part : parts) { if (part.isEmpty()) continue; if (builder.length() > 0) builder.append(' '); builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)); } return builder.toString(); }
    private record SelectorState(String query, int page) {}
}
