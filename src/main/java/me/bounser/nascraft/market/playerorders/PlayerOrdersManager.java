package me.bounser.nascraft.market.playerorders;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.managers.InventoryManager;
import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.unit.Item;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Player-to-player procurement orders. Money is escrowed when an order is
 * created, suppliers can fulfil partial amounts and owners can collect the
 * delivered items at any time.
 */
public final class PlayerOrdersManager implements Listener {
    public static final String TITLE = "§8§lPlayer Orders";
    private static final int PAGE_SIZE = 45;
    private static final int MAX_ORDER_AMOUNT = 1_000_000;
    private static PlayerOrdersManager instance;

    private final File file;
    private final YamlConfiguration storage;
    private final Map<UUID, PlayerOrder> orders = new HashMap<>();
    private final Map<UUID, List<UUID>> viewedOrders = new HashMap<>();

    private PlayerOrdersManager() {
        file = new File(Nascraft.getInstance().getDataFolder(), "player-orders.yml");
        storage = YamlConfiguration.loadConfiguration(file);
        load();
    }

    public static PlayerOrdersManager getInstance() {
        if (instance == null) instance = new PlayerOrdersManager();
        return instance;
    }

    public synchronized List<PlayerOrder> getOrders() {
        return orders.values().stream()
                .sorted(Comparator.comparingLong(PlayerOrder::createdAt).reversed())
                .toList();
    }

    public synchronized PlayerOrder getOrder(UUID id) {
        return orders.get(id);
    }

    public synchronized String create(Player player, String identifier, int amount, double unitPrice) {
        if (player == null) return "Only players can create orders.";
        Item item = MarketManager.getInstance().getItem(identifier.toLowerCase(Locale.ROOT));
        if (item == null || !item.isParent()) return "Unknown market item: " + identifier;
        if (amount <= 0 || amount > MAX_ORDER_AMOUNT) return "Amount must be between 1 and " + MAX_ORDER_AMOUNT + ".";
        if (!Double.isFinite(unitPrice) || unitPrice <= 0) return "Price per item must be greater than zero.";

        double escrow = unitPrice * amount;
        if (!Double.isFinite(escrow) || escrow <= 0) return "The total order value is invalid.";
        Economy economy = Nascraft.getEconomy();
        if (economy == null) return "Vault economy is not available.";
        if (!economy.has(player, escrow)) return "You do not have enough money. Required: " + formatMoney(escrow);

        EconomyResponse withdrawal = economy.withdrawPlayer(player, escrow);
        if (!withdrawal.transactionSuccess()) return "Could not reserve the order money: " + withdrawal.errorMessage;

        PlayerOrder order = new PlayerOrder(UUID.randomUUID(), player.getUniqueId(), item.getIdentifier(), amount, 0, 0, unitPrice, System.currentTimeMillis());
        orders.put(order.id(), order);
        if (!save()) {
            orders.remove(order.id());
            economy.depositPlayer(player, escrow);
            return "Could not persist the order; no money was kept.";
        }
        return null;
    }

    public synchronized String deliver(Player player, UUID orderId, int requestedAmount) {
        PlayerOrder order = orders.get(orderId);
        if (order == null) return "Order not found.";
        if (order.owner().equals(player.getUniqueId())) return "You cannot fulfil your own order.";
        int remaining = order.totalAmount() - order.deliveredAmount();
        if (remaining <= 0) return "This order is already complete.";

        Item marketItem = MarketManager.getInstance().getItem(order.identifier());
        if (marketItem == null) return "The market item for this order no longer exists.";
        int available = countItems(player, marketItem.getItemStack());
        int amount = requestedAmount <= 0 ? Math.min(available, remaining) : Math.min(Math.min(requestedAmount, available), remaining);
        if (amount <= 0) return "You do not have matching items to deliver.";

        double payout = amount * order.unitPrice();
        Economy economy = Nascraft.getEconomy();
        if (economy == null) return "Vault economy is not available.";

        InventoryManager.removeItems(player, marketItem.getItemStack(), amount);
        EconomyResponse deposit = economy.depositPlayer(player, payout);
        if (!deposit.transactionSuccess()) {
            InventoryManager.addItemsToInventory(player, marketItem.getItemStack(), amount);
            return "Could not pay the delivery: " + deposit.errorMessage;
        }

        PlayerOrder updated = order.withDelivered(order.deliveredAmount() + amount);
        orders.put(orderId, updated);
        if (!save()) {
            // The delivery happened, so keep the in-memory state and report loudly instead of
            // rolling back money/items and risking a duplicated payout on the next interaction.
            Nascraft.getInstance().getLogger().severe("Could not persist player order " + orderId + " after delivery.");
        }
        return null;
    }

    public synchronized String collect(Player player, UUID orderId) {
        PlayerOrder order = orders.get(orderId);
        if (order == null) return "Order not found.";
        if (!order.owner().equals(player.getUniqueId())) return "Only the order owner can collect deliveries.";
        int available = order.deliveredAmount() - order.collectedAmount();
        if (available <= 0) return "There are no new delivered items to collect.";

        Item marketItem = MarketManager.getInstance().getItem(order.identifier());
        if (marketItem == null) return "The market item for this order no longer exists.";
        InventoryManager.addItemsToInventory(player, marketItem.getItemStack(), available);

        int collected = order.collectedAmount() + available;
        if (order.deliveredAmount() >= order.totalAmount() && collected >= order.totalAmount()) orders.remove(orderId);
        else orders.put(orderId, order.withCollected(collected));
        save();
        return null;
    }

    public synchronized String cancel(Player player, UUID orderId) {
        PlayerOrder order = orders.get(orderId);
        if (order == null) return "Order not found.";
        if (!order.owner().equals(player.getUniqueId())) return "Only the order owner can cancel it.";

        int unfilled = order.totalAmount() - order.deliveredAmount();
        double refund = unfilled * order.unitPrice();
        Economy economy = Nascraft.getEconomy();
        if (economy == null) return "Vault economy is not available.";
        if (refund > 0) {
            EconomyResponse deposit = economy.depositPlayer(player, refund);
            if (!deposit.transactionSuccess()) return "Could not refund the remaining escrow: " + deposit.errorMessage;
        }

        // Delivered-but-uncollected items remain claimable by turning the cancelled order into
        // a fully-filled collection record. If nothing is waiting, it can be removed immediately.
        if (order.deliveredAmount() > order.collectedAmount()) {
            orders.put(orderId, new PlayerOrder(order.id(), order.owner(), order.identifier(), order.deliveredAmount(), order.deliveredAmount(), order.collectedAmount(), order.unitPrice(), order.createdAt()));
        } else {
            orders.remove(orderId);
        }
        save();
        return null;
    }

    public void open(Player player) {
        List<PlayerOrder> snapshot = getOrders();
        List<UUID> ids = new ArrayList<>();
        Inventory inventory = Nascraft.getInstance().getServer().createInventory(player, 54, TITLE);
        int limit = Math.min(PAGE_SIZE, snapshot.size());
        for (int slot = 0; slot < limit; slot++) {
            PlayerOrder order = snapshot.get(slot);
            Item marketItem = MarketManager.getInstance().getItem(order.identifier());
            ItemStack stack = marketItem == null ? new ItemStack(Material.BARRIER) : marketItem.getItemStack().clone();
            stack.setAmount(1);
            ItemMeta meta = stack.getItemMeta();
            String itemName = marketItem == null ? order.identifier() : marketItem.getName();
            meta.setDisplayName(ChatColor.GOLD + itemName + ChatColor.DARK_GRAY + " • " + order.id().toString().substring(0, 8));
            int remaining = order.totalAmount() - order.deliveredAmount();
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Requested: " + ChatColor.WHITE + order.totalAmount());
            lore.add(ChatColor.GRAY + "Delivered: " + ChatColor.GREEN + order.deliveredAmount() + ChatColor.GRAY + "/" + order.totalAmount());
            lore.add(ChatColor.GRAY + "Remaining: " + ChatColor.WHITE + remaining);
            lore.add(ChatColor.GRAY + "Price/item: " + ChatColor.GOLD + formatMoney(order.unitPrice()));
            lore.add(ChatColor.GRAY + "Remaining payout: " + ChatColor.GOLD + formatMoney(remaining * order.unitPrice()));
            lore.add("");
            if (order.owner().equals(player.getUniqueId())) {
                lore.add(ChatColor.GREEN + "Left click: collect delivered items");
                lore.add(ChatColor.RED + "Right click: cancel order");
            } else {
                lore.add(ChatColor.GREEN + "Left click: deliver as many as possible");
            }
            meta.setLore(lore);
            stack.setItemMeta(meta);
            inventory.setItem(slot, stack);
            ids.add(order.id());
        }

        ItemStack help = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta helpMeta = help.getItemMeta();
        helpMeta.setDisplayName(ChatColor.GREEN + "Create an order");
        helpMeta.setLore(List.of(ChatColor.GRAY + "/orders create <item> <amount> <price/item>", ChatColor.GRAY + "Money is reserved immediately."));
        help.setItemMeta(helpMeta);
        inventory.setItem(49, help);
        viewedOrders.put(player.getUniqueId(), ids);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(TITLE) || !(event.getWhoClicked() instanceof Player player)) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= PAGE_SIZE) return;
        List<UUID> ids = viewedOrders.get(player.getUniqueId());
        if (ids == null || slot >= ids.size()) return;
        UUID orderId = ids.get(slot);
        PlayerOrder order = getOrder(orderId);
        if (order == null) { open(player); return; }

        String error;
        if (order.owner().equals(player.getUniqueId())) {
            error = event.isRightClick() ? cancel(player, orderId) : collect(player, orderId);
        } else {
            error = deliver(player, orderId, 0);
        }
        if (error != null) player.sendMessage(ChatColor.RED + error);
        else player.sendMessage(ChatColor.LIGHT_PURPLE + "Order updated successfully.");
        open(player);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().equals(TITLE)) viewedOrders.remove(event.getPlayer().getUniqueId());
    }

    private int countItems(Player player, ItemStack template) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && MarketManager.getInstance().isSimilarEnough(stack, template)) count += stack.getAmount();
        }
        return count;
    }

    private void load() {
        ConfigurationSection root = storage.getConfigurationSection("orders");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                String path = "orders." + key + ".";
                PlayerOrder order = new PlayerOrder(
                        id,
                        UUID.fromString(storage.getString(path + "owner", "")),
                        storage.getString(path + "identifier", ""),
                        storage.getInt(path + "total"),
                        storage.getInt(path + "delivered"),
                        storage.getInt(path + "collected"),
                        storage.getDouble(path + "unit-price"),
                        storage.getLong(path + "created-at")
                );
                if (order.totalAmount() > 0 && order.unitPrice() > 0 && !order.identifier().isBlank()) orders.put(id, order);
            } catch (RuntimeException exception) {
                Nascraft.getInstance().getLogger().warning("Ignoring invalid player order " + key + ": " + exception.getMessage());
            }
        }
    }

    private boolean save() {
        storage.set("orders", null);
        for (PlayerOrder order : orders.values()) {
            String path = "orders." + order.id() + ".";
            storage.set(path + "owner", order.owner().toString());
            storage.set(path + "identifier", order.identifier());
            storage.set(path + "total", order.totalAmount());
            storage.set(path + "delivered", order.deliveredAmount());
            storage.set(path + "collected", order.collectedAmount());
            storage.set(path + "unit-price", order.unitPrice());
            storage.set(path + "created-at", order.createdAt());
        }
        try {
            storage.save(file);
            return true;
        } catch (IOException exception) {
            Nascraft.getInstance().getLogger().severe("Could not save player-orders.yml: " + exception.getMessage());
            return false;
        }
    }

    private String formatMoney(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    public record PlayerOrder(UUID id, UUID owner, String identifier, int totalAmount, int deliveredAmount,
                              int collectedAmount, double unitPrice, long createdAt) {
        public PlayerOrder withDelivered(int delivered) {
            return new PlayerOrder(id, owner, identifier, totalAmount, delivered, collectedAmount, unitPrice, createdAt);
        }
        public PlayerOrder withCollected(int collected) {
            return new PlayerOrder(id, owner, identifier, totalAmount, deliveredAmount, collected, unitPrice, createdAt);
        }
    }
}
