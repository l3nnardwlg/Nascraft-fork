package me.bounser.nascraft.commands.search;

import me.bounser.nascraft.commands.Command;
import me.bounser.nascraft.input.ChatInputManager;
import me.bounser.nascraft.inventorygui.SearchResultsMenu;
import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.unit.Item;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Direct market search command: /search <query>. */
public class SearchCommand extends Command {

    public SearchCommand() {
        super("search", new String[]{"find"}, "Search the Nascraft market", "nascraft.market");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by a player.");
            return;
        }

        ChatInputManager.getInstance().clear(player);

        if (args.length == 0) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /search <item or category>");
            return;
        }

        String query = normalize(String.join(" ", args));
        List<Item> matches = MarketManager.getInstance().getAllItems().stream()
                .filter(item -> matches(item, query))
                .sorted(Comparator.comparingInt((Item item) -> rank(item, query))
                        .thenComparing(item -> normalize(item.getCategory().getIdentifier()))
                        .thenComparing(item -> normalize(item.getName())))
                .toList();

        if (matches.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No market items found for: " + String.join(" ", args));
            return;
        }

        new SearchResultsMenu(player, String.join(" ", args).trim(), matches);
    }

    private boolean matches(Item item, String query) {
        return normalize(item.getIdentifier()).contains(query)
                || normalize(item.getName()).contains(query)
                || normalize(item.getItemStack().getType().name()).contains(query)
                || normalize(item.getCategory().getIdentifier()).contains(query)
                || normalize(item.getCategory().getFormattedDisplayName()).contains(query);
    }

    private int rank(Item item, String query) {
        String identifier = normalize(item.getIdentifier());
        String name = normalize(item.getName());
        String material = normalize(item.getItemStack().getType().name());
        String category = normalize(item.getCategory().getIdentifier());
        if (identifier.equals(query) || name.equals(query) || material.equals(query)) return 0;
        if (identifier.startsWith(query) || name.startsWith(query) || material.startsWith(query)) return 1;
        if (category.equals(query) || category.startsWith(query)) return 2;
        return 3;
    }

    private String normalize(String value) {
        String stripped = value == null ? "" : ChatColor.stripColor(value);
        return (stripped == null ? "" : stripped).toLowerCase(Locale.ROOT).trim().replace(' ', '_');
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
