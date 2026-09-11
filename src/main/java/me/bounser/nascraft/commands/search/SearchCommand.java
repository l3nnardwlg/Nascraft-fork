package me.bounser.nascraft.commands.search;

import me.bounser.nascraft.commands.Command;
import me.bounser.nascraft.input.ChatInputManager;
import me.bounser.nascraft.inventorygui.MarketSearchService;
import me.bounser.nascraft.inventorygui.SearchResultsMenu;
import me.bounser.nascraft.market.unit.Item;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

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

        // A player may invoke /search while the compass chat prompt is still open.
        // Drop that stale one-shot session so a later "cancel" or chat message cannot
        // unexpectedly close/replace the newly opened results inventory.
        ChatInputManager.getInstance().clear(player);

        if (args.length == 0) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /search <item or category>");
            return;
        }

        String query = String.join(" ", args).trim();
        List<Item> matches = MarketSearchService.findMatches(query);

        if (matches.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No market items found for: " + query);
            return;
        }

        new SearchResultsMenu(player, query, matches);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
