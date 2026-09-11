package me.bounser.nascraft.auction;

import me.bounser.nascraft.commands.Command;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AuctionHouseCommand extends Command {

    public AuctionHouseCommand() {
        super("ah", new String[]{"auctionhouse", "auction"}, "Open and manage the Nascraft Auction House", null);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use the Auction House.");
            return;
        }

        AuctionHouseManager manager = AuctionHouseManager.getInstance();
        if (args.length == 0 || args[0].equalsIgnoreCase("browse") || args[0].equalsIgnoreCase("open")) {
            manager.openPublic(player);
            return;
        }

        if (args[0].equalsIgnoreCase("mine") || args[0].equalsIgnoreCase("my")) {
            manager.openMine(player);
            return;
        }

        if (args[0].equalsIgnoreCase("sell") || args[0].equalsIgnoreCase("list")) {
            if (args.length < 2 || args.length > 3) {
                usage(player);
                return;
            }
            double price;
            int hours = 24;
            try {
                price = Double.parseDouble(args[1].replace(',', '.'));
                if (args.length == 3) hours = Integer.parseInt(args[2]);
            } catch (NumberFormatException exception) {
                player.sendMessage(ChatColor.RED + "Price and duration must be valid numbers.");
                return;
            }

            String error = manager.createListing(player, price, hours);
            if (error != null) {
                player.sendMessage(ChatColor.RED + error);
                return;
            }
            player.sendMessage(ChatColor.GREEN + "Item listed in the Auction House for $" + price + " for " + hours + "h.");
            manager.openMine(player);
            return;
        }

        usage(player);
    }

    private void usage(Player player) {
        player.sendMessage(ChatColor.GOLD + "Auction House");
        player.sendMessage(ChatColor.GRAY + "/ah" + ChatColor.WHITE + " - browse listings");
        player.sendMessage(ChatColor.GRAY + "/ah mine" + ChatColor.WHITE + " - manage your listings");
        player.sendMessage(ChatColor.GRAY + "/ah sell <price> [hours]" + ChatColor.WHITE + " - list the item in your main hand");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return filter(List.of("browse", "mine", "sell"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("sell")) return List.of("100", "500", "1000");
        if (args.length == 3 && args[0].equalsIgnoreCase("sell")) return List.of("1", "12", "24", "72", "168");
        return List.of();
    }

    private List<String> filter(List<String> values, String prefix) {
        String normalized = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(normalized)) result.add(value);
        }
        return result;
    }
}
