package me.bounser.nascraft.inventorygui;

import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.unit.Item;
import org.bukkit.ChatColor;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Shared search logic for command and GUI search flows. */
public final class MarketSearchService {

    private MarketSearchService() {}

    public static List<Item> findMatches(String rawQuery) {
        String query = normalize(rawQuery);
        if (query.isBlank()) return List.of();

        return MarketManager.getInstance().getAllItems().stream()
                .filter(item -> matches(item, query))
                .sorted(Comparator.comparingInt((Item item) -> matchRank(item, query))
                        .thenComparing(item -> normalize(item.getCategory().getIdentifier()))
                        .thenComparing(item -> normalize(item.getName())))
                .toList();
    }

    private static boolean matches(Item item, String query) {
        return normalize(item.getIdentifier()).contains(query)
                || normalize(item.getName()).contains(query)
                || normalize(item.getItemStack().getType().name()).contains(query)
                || normalize(item.getCategory().getIdentifier()).contains(query)
                || normalize(item.getCategory().getFormattedDisplayName()).contains(query);
    }

    private static int matchRank(Item item, String query) {
        String identifier = normalize(item.getIdentifier());
        String name = normalize(item.getName());
        String material = normalize(item.getItemStack().getType().name());
        String category = normalize(item.getCategory().getIdentifier());

        if (identifier.equals(query) || name.equals(query) || material.equals(query)) return 0;
        if (identifier.startsWith(query) || name.startsWith(query) || material.startsWith(query)) return 1;
        if (category.equals(query) || category.startsWith(query)) return 2;
        return 3;
    }

    public static String normalize(String value) {
        String stripped = value == null ? "" : ChatColor.stripColor(value);
        return (stripped == null ? "" : stripped)
                .toLowerCase(Locale.ROOT)
                .trim()
                .replace(' ', '_');
    }
}
