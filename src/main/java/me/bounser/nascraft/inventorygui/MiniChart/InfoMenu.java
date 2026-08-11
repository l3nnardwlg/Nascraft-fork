package me.bounser.nascraft.inventorygui.MiniChart;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.chart.price.ChartType;
import me.bounser.nascraft.chart.price.ItemChartReduced;
import me.bounser.nascraft.config.lang.Lang;
import me.bounser.nascraft.config.lang.Message;
import me.bounser.nascraft.database.DatabaseManager;
import me.bounser.nascraft.formatter.Formatter;
import me.bounser.nascraft.formatter.Style;
import me.bounser.nascraft.inventorygui.MenuPage;
import me.bounser.nascraft.inventorygui.MarketMenuManager;
import me.bounser.nascraft.market.unit.Item;
import me.bounser.nascraft.market.unit.stats.Instant;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class InfoMenu implements MenuPage {

    private static final int GRAPH_ROWS = 5;
    private static final int GRAPH_COLUMNS = 9;

    private final Player player;
    private final Item item;
    private Inventory gui;

    public InfoMenu(Player player, Item item) {
        this.player = player;
        this.item = item;
        open();
    }

    @Override
    public void open() {
        Component titleComponent = MiniMessage.miniMessage().deserialize(Lang.get().message(Message.GUI_INFO_TITLE));
        String title = LegacyComponentSerializer.legacySection().serialize(titleComponent);
        gui = Bukkit.createInventory(null, 54, title);

        renderGraph();
        renderFooter();

        player.openInventory(gui);
        player.setMetadata("NascraftMenu", new FixedMetadataValue(Nascraft.getInstance(), "info-menu-" + item.getIdentifier()));
    }

    private void renderGraph() {
        ItemStack background = MarketMenuManager.getInstance().generateItemStack(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < GRAPH_ROWS * GRAPH_COLUMNS; slot++) gui.setItem(slot, background);

        List<Double> prices = loadDayPrices();
        if (prices.isEmpty()) prices.add(safeCurrentPrice());

        List<Double> sampled = sample(prices, GRAPH_COLUMNS);
        double min = sampled.stream().mapToDouble(Double::doubleValue).min().orElse(safeCurrentPrice());
        double max = sampled.stream().mapToDouble(Double::doubleValue).max().orElse(safeCurrentPrice());
        boolean positive = sampled.get(sampled.size() - 1) >= sampled.get(0);
        Material graphMaterial = positive ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;

        for (int column = 0; column < sampled.size(); column++) {
            double price = sampled.get(column);
            int row;
            if (Math.abs(max - min) < 0.0000001) {
                row = GRAPH_ROWS / 2;
            } else {
                double normalized = (price - min) / (max - min);
                row = GRAPH_ROWS - 1 - (int) Math.round(normalized * (GRAPH_ROWS - 1));
            }

            List<String> lore = List.of(
                    "§7Older §8→ §7Newer",
                    "§7Price: §f" + Formatter.format(item.getCurrency(), price, Style.ROUND_BASIC)
            );
            gui.setItem(row * GRAPH_COLUMNS + column,
                    MarketMenuManager.getInstance().generateItemStack(graphMaterial, "§bPrice point " + (column + 1), lore));
        }
    }

    private void renderFooter() {
        double current = safeCurrentPrice();
        List<Double> prices = loadDayPrices();
        if (prices.isEmpty()) prices.add(current);

        double high = prices.stream().mapToDouble(Double::doubleValue).max().orElse(current);
        double low = prices.stream().mapToDouble(Double::doubleValue).min().orElse(current);
        double first = prices.get(0);
        double last = prices.get(prices.size() - 1);
        double change = first > 0 ? ((last / first) - 1.0) * 100.0 : 0.0;

        gui.setItem(45, MarketMenuManager.getInstance().generateItemStack(
                Material.BARRIER,
                "§cClose to return",
                List.of("§7Press your inventory key to return", "§7to the market item menu.")));

        gui.setItem(47, MarketMenuManager.getInstance().generateItemStack(
                Material.EMERALD,
                "§a24h High",
                List.of("§f" + Formatter.format(item.getCurrency(), high, Style.ROUND_BASIC))));

        gui.setItem(48, MarketMenuManager.getInstance().generateItemStack(
                item.getItemStack().getType(),
                "§f" + item.getName(),
                List.of("§7Current price", "§f" + Formatter.format(item.getCurrency(), current, Style.ROUND_BASIC))));

        gui.setItem(49, MarketMenuManager.getInstance().generateItemStack(
                Material.CLOCK,
                "§624 Hour Chart",
                List.of("§7Minecraft-native chart view", "§7Left = older, right = newer")));

        gui.setItem(50, MarketMenuManager.getInstance().generateItemStack(
                Material.REDSTONE,
                "§c24h Low",
                List.of("§f" + Formatter.format(item.getCurrency(), low, Style.ROUND_BASIC))));

        String changeColor = change >= 0 ? "§a" : "§c";
        gui.setItem(51, MarketMenuManager.getInstance().generateItemStack(
                change >= 0 ? Material.LIME_DYE : Material.RED_DYE,
                "§e24h Change",
                List.of(changeColor + String.format("%+.2f%%", change))));

        gui.setItem(53, MarketMenuManager.getInstance().generateItemStack(
                Material.PAPER,
                "§bChart help",
                List.of(
                        "§7Each column represents a sampled",
                        "§7price point from the last 24 hours.",
                        "§7Green = finish above start.",
                        "§7Red = finish below start."
                )));
    }

    private List<Double> loadDayPrices() {
        List<Double> prices = new ArrayList<>();
        Item dataItem = item.isParent() ? item : item.getParent();
        if (dataItem == null) dataItem = item;

        try {
            List<Instant> history = DatabaseManager.get().getDatabase().getDayPrices(dataItem);
            if (history != null) {
                history.stream()
                        .filter(point -> point != null && point.getLocalDateTime() != null)
                        .filter(point -> Double.isFinite(point.getPrice()) && point.getPrice() > 0)
                        .sorted(Comparator.comparing(Instant::getLocalDateTime))
                        .forEach(point -> prices.add(point.getPrice()));
            }
        } catch (Exception exception) {
            Nascraft.getInstance().getLogger().warning(
                    "Could not load in-game chart history for " + item.getIdentifier() + ": " + exception.getMessage());
        }

        if (prices.size() < 2) {
            double current = safeCurrentPrice();
            prices.clear();
            prices.add(current);
            prices.add(current);
        }
        return prices;
    }

    private List<Double> sample(List<Double> values, int count) {
        List<Double> sampled = new ArrayList<>(count);
        if (values.size() == 1) {
            for (int i = 0; i < count; i++) sampled.add(values.get(0));
            return sampled;
        }

        for (int i = 0; i < count; i++) {
            int index = (int) Math.round((values.size() - 1) * (i / (double) (count - 1)));
            sampled.add(values.get(index));
        }
        return sampled;
    }

    private double safeCurrentPrice() {
        double current = item.getPrice().getValue();
        if (!Double.isFinite(current) || current <= 0) current = item.getPrice().getInitialValue();
        return Double.isFinite(current) && current > 0 ? current : 0.01;
    }

    @Override
    public void close() {
    }

    @Override
    public void update() {
        renderGraph();
        renderFooter();
    }

    public static BufferedImage getMapImage(Item item, ChartType type) {
        return ItemChartReduced.getImage(item, type);
    }
}
