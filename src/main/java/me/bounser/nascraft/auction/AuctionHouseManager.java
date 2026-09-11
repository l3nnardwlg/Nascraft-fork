package me.bounser.nascraft.auction;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.managers.MoneyManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AuctionHouseManager implements Listener {
    private static final String TITLE_PUBLIC = "§8§lAuction House";
    private static final String TITLE_MINE = "§8§lMy Auctions";
    private static final String TITLE_CONFIRM = "§8§lConfirm Purchase";
    private static final int PAGE_SIZE = 45;
    private static final DecimalFormat PRICE = new DecimalFormat("#,##0.##");
    private static AuctionHouseManager instance;

    private final Nascraft plugin;
    private final File storageFile;
    private final YamlConfiguration storage;
    private final Map<UUID, AuctionListing> listings = new LinkedHashMap<>();
    private final Map<UUID, MenuSession> sessions = new HashMap<>();

    private AuctionHouseManager(Nascraft plugin) {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "auction-house.yml");
        this.storage = YamlConfiguration.loadConfiguration(storageFile);
        load();
    }

    public static synchronized AuctionHouseManager init(Nascraft plugin) {
        if (instance == null) instance = new AuctionHouseManager(plugin);
        return instance;
    }

    public static AuctionHouseManager getInstance() {
        if (instance == null) throw new IllegalStateException("AuctionHouseManager is not initialized");
        return instance;
    }

    public synchronized String createListing(Player seller, double price, int hours) {
        if (!Double.isFinite(price) || price <= 0) return "Price must be greater than 0.";
        int maxHours = Math.max(1, plugin.getConfig().getInt("auction-house.max-duration-hours", 168));
        if (hours < 1 || hours > maxHours) return "Duration must be between 1 and " + maxHours + " hours.";

        int maxListings = Math.max(1, plugin.getConfig().getInt("auction-house.max-listings-per-player", 10));
        long ownActive = listings.values().stream()
                .filter(l -> l.sellerUuid().equals(seller.getUniqueId()) && !l.isExpired())
                .count();
        if (ownActive >= maxListings) return "You already have the maximum of " + maxListings + " active listings.";

        ItemStack hand = seller.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) return "Hold the item you want to sell in your main hand.";

        ItemStack listed = hand.clone();
        UUID id = UUID.randomUUID();
        long now = System.currentTimeMillis();
        AuctionListing listing = new AuctionListing(id, seller.getUniqueId(), seller.getName(), listed, price, now,
                now + Duration.ofHours(hours).toMillis());

        seller.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        listings.put(id, listing);
        if (!save()) {
            listings.remove(id);
            seller.getInventory().setItemInMainHand(listed);
            return "Could not persist the listing. Nothing was listed.";
        }

        return null;
    }

    public void openPublic(Player player) {
        openPage(player, false, 0);
    }

    public void openMine(Player player) {
        openPage(player, true, 0);
    }

    private synchronized void openPage(Player player, boolean mine, int requestedPage) {
        List<AuctionListing> visible = mine ? mine(player.getUniqueId()) : active();
        int maxPage = Math.max(0, (visible.size() - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        Inventory inventory = Bukkit.createInventory(player, 54, mine ? TITLE_MINE : TITLE_PUBLIC);
        Map<Integer, UUID> slotToListing = new HashMap<>();

        int start = page * PAGE_SIZE;
        for (int slot = 0; slot < PAGE_SIZE && start + slot < visible.size(); slot++) {
            AuctionListing listing = visible.get(start + slot);
            inventory.setItem(slot, listingIcon(listing, mine));
            slotToListing.put(slot, listing.id());
        }

        ItemStack filler = button(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int slot = 45; slot < 54; slot++) inventory.setItem(slot, filler);
        if (page > 0) inventory.setItem(45, button(Material.ARROW, ChatColor.YELLOW + "§lPrevious page"));
        inventory.setItem(47, button(Material.SUNFLOWER, ChatColor.GOLD + "§lRefresh"));
        inventory.setItem(49, button(Material.PAPER,
                ChatColor.GOLD + "§lAuction House",
                ChatColor.GRAY + "Page " + (page + 1) + "/" + (maxPage + 1),
                ChatColor.GRAY + "Listings: " + visible.size()));
        inventory.setItem(51, button(Material.CHEST,
                mine ? ChatColor.AQUA + "§lBrowse auctions" : ChatColor.AQUA + "§lMy auctions",
                mine ? ChatColor.GRAY + "View active public listings." : ChatColor.GRAY + "View, cancel and reclaim your listings."));
        if (page < maxPage) inventory.setItem(53, button(Material.ARROW, ChatColor.YELLOW + "§lNext page"));

        sessions.put(player.getUniqueId(), new MenuSession(mine ? View.MINE : View.PUBLIC, page, slotToListing, null));
        player.openInventory(inventory);
    }

    private synchronized void openConfirm(Player player, AuctionListing listing, MenuSession previous) {
        Inventory inventory = Bukkit.createInventory(player, 27, TITLE_CONFIRM);
        ItemStack filler = button(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < 27; slot++) inventory.setItem(slot, filler);
        inventory.setItem(13, listingIcon(listing, false));
        inventory.setItem(11, button(Material.LIME_CONCRETE,
                ChatColor.GREEN + "§lBUY",
                ChatColor.GRAY + "Price: " + ChatColor.GOLD + "$" + PRICE.format(listing.price()),
                ChatColor.YELLOW + "Click to confirm."));
        inventory.setItem(15, button(Material.RED_CONCRETE,
                ChatColor.RED + "§lCANCEL",
                ChatColor.GRAY + "Return to the auction list."));
        sessions.put(player.getUniqueId(), new MenuSession(View.CONFIRM, previous.page(), previous.slotToListing(), listing.id()));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!title.equals(TITLE_PUBLIC) && !title.equals(TITLE_MINE) && !title.equals(TITLE_CONFIRM)) return;
        event.setCancelled(true);

        MenuSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;

        if (session.view() == View.CONFIRM) {
            if (slot == 11 && session.pendingListing() != null) {
                String error = purchase(player, session.pendingListing());
                if (error == null) player.sendMessage(ChatColor.GREEN + "Auction purchased successfully.");
                else player.sendMessage(ChatColor.RED + error);
                openPage(player, false, session.page());
            } else if (slot == 15) {
                openPage(player, false, session.page());
            }
            return;
        }

        if (slot == 45 && session.page() > 0) {
            openPage(player, session.view() == View.MINE, session.page() - 1);
            return;
        }
        if (slot == 47) {
            openPage(player, session.view() == View.MINE, session.page());
            return;
        }
        if (slot == 51) {
            openPage(player, session.view() != View.MINE, 0);
            return;
        }
        if (slot == 53) {
            openPage(player, session.view() == View.MINE, session.page() + 1);
            return;
        }

        UUID listingId = session.slotToListing().get(slot);
        if (listingId == null) return;
        AuctionListing listing = listings.get(listingId);
        if (listing == null) {
            openPage(player, session.view() == View.MINE, session.page());
            return;
        }

        if (session.view() == View.MINE) {
            String error = reclaim(player, listingId);
            if (error == null) player.sendMessage(ChatColor.GREEN + "Auction item returned to you.");
            else player.sendMessage(ChatColor.RED + error);
            openPage(player, true, session.page());
        } else if (listing.sellerUuid().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "This is your own listing. Open 'My auctions' to reclaim it.");
        } else {
            openConfirm(player, listing, session);
        }
    }

    private synchronized String purchase(Player buyer, UUID listingId) {
        AuctionListing listing = listings.get(listingId);
        if (listing == null || listing.isExpired()) return "This listing is no longer available.";
        if (listing.sellerUuid().equals(buyer.getUniqueId())) return "You cannot buy your own listing.";
        if (!canFit(buyer, listing.item())) return "You do not have enough inventory space.";

        OfflinePlayer seller = Bukkit.getOfflinePlayer(listing.sellerUuid());
        MoneyManager.TransferResult transfer = MoneyManager.getInstance().transfer(buyer, seller, listing.price());
        if (transfer != MoneyManager.TransferResult.SUCCESS) {
            return switch (transfer) {
                case INSUFFICIENT_FUNDS -> "You do not have enough money.";
                case ECONOMY_UNAVAILABLE -> "No Vault economy provider is available.";
                case WITHDRAW_FAILED -> "The payment could not be withdrawn.";
                case DEPOSIT_FAILED -> "The seller could not be paid. Your payment was refunded.";
                default -> "The payment failed.";
            };
        }

        listings.remove(listingId);
        if (!save()) plugin.getLogger().warning("Auction purchase succeeded but auction-house.yml could not be saved immediately: " + listingId);
        buyer.getInventory().addItem(listing.item().clone());
        return null;
    }

    private synchronized String reclaim(Player player, UUID listingId) {
        AuctionListing listing = listings.get(listingId);
        if (listing == null) return "Listing not found.";
        if (!listing.sellerUuid().equals(player.getUniqueId())) return "That listing does not belong to you.";
        if (!canFit(player, listing.item())) return "You do not have enough inventory space.";

        listings.remove(listingId);
        if (!save()) {
            listings.put(listingId, listing);
            return "Could not save the auction state. Try again.";
        }
        player.getInventory().addItem(listing.item().clone());
        return null;
    }

    private boolean canFit(Player player, ItemStack stack) {
        int remaining = stack.getAmount();
        for (ItemStack current : player.getInventory().getStorageContents()) {
            if (current == null || current.getType() == Material.AIR) return true;
            if (current.isSimilar(stack)) {
                remaining -= Math.max(0, current.getMaxStackSize() - current.getAmount());
                if (remaining <= 0) return true;
            }
        }
        return remaining <= 0;
    }

    private List<AuctionListing> active() {
        long now = System.currentTimeMillis();
        return listings.values().stream()
                .filter(l -> l.expiresAt() > now)
                .sorted(Comparator.comparingLong(AuctionListing::createdAt).reversed())
                .toList();
    }

    private List<AuctionListing> mine(UUID seller) {
        return listings.values().stream()
                .filter(l -> l.sellerUuid().equals(seller))
                .sorted(Comparator.comparing(AuctionListing::isExpired)
                        .thenComparing(Comparator.comparingLong(AuctionListing::createdAt).reversed()))
                .toList();
    }

    private ItemStack listingIcon(AuctionListing listing, boolean mine) {
        ItemStack icon = listing.item().clone();
        ItemMeta meta = icon.getItemMeta();
        List<String> lore = meta != null && meta.hasLore() && meta.getLore() != null
                ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        if (!lore.isEmpty()) lore.add("");
        lore.add(ChatColor.GRAY + "Seller: " + ChatColor.WHITE + listing.sellerName());
        lore.add(ChatColor.GRAY + "Price: " + ChatColor.GOLD + "$" + PRICE.format(listing.price()));
        if (listing.isExpired()) lore.add(ChatColor.RED + "Expired");
        else lore.add(ChatColor.GRAY + "Expires in: " + ChatColor.WHITE + formatRemaining(listing.expiresAt()));
        lore.add("");
        if (mine) lore.add(ChatColor.YELLOW + "Click to reclaim/cancel this listing.");
        else lore.add(ChatColor.GREEN + "Click to buy.");
        if (meta != null) {
            meta.setLore(lore);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private String formatRemaining(long expiresAt) {
        long seconds = Math.max(0, (expiresAt - System.currentTimeMillis()) / 1000);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 0) return hours + "h " + minutes + "m";
        return Math.max(1, minutes) + "m";
    }

    private ItemStack button(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) meta.setLore(Arrays.asList(lore));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void load() {
        ConfigurationSection root = storage.getConfigurationSection("listings");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                String path = "listings." + key + ".";
                UUID seller = UUID.fromString(storage.getString(path + "seller-uuid"));
                String sellerName = storage.getString(path + "seller-name", "Unknown");
                ItemStack item = storage.getItemStack(path + "item");
                double price = storage.getDouble(path + "price");
                long createdAt = storage.getLong(path + "created-at");
                long expiresAt = storage.getLong(path + "expires-at");
                if (item == null || item.getType() == Material.AIR || !Double.isFinite(price) || price <= 0) continue;
                listings.put(id, new AuctionListing(id, seller, sellerName, item, price, createdAt, expiresAt));
            } catch (Exception exception) {
                plugin.getLogger().warning("Skipping invalid auction listing " + key + ": " + exception.getMessage());
            }
        }
    }

    private boolean save() {
        storage.set("listings", null);
        for (AuctionListing listing : listings.values()) {
            String path = "listings." + listing.id() + ".";
            storage.set(path + "seller-uuid", listing.sellerUuid().toString());
            storage.set(path + "seller-name", listing.sellerName());
            storage.set(path + "item", listing.item());
            storage.set(path + "price", listing.price());
            storage.set(path + "created-at", listing.createdAt());
            storage.set(path + "expires-at", listing.expiresAt());
        }
        try {
            storage.save(storageFile);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save auction-house.yml: " + exception.getMessage());
            return false;
        }
    }

    public record AuctionListing(UUID id, UUID sellerUuid, String sellerName, ItemStack item,
                                 double price, long createdAt, long expiresAt) {
        public boolean isExpired() { return expiresAt <= System.currentTimeMillis(); }
    }

    private enum View { PUBLIC, MINE, CONFIRM }
    private record MenuSession(View view, int page, Map<Integer, UUID> slotToListing, UUID pendingListing) {}
}
