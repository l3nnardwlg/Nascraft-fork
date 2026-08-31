package me.bounser.nascraft.commands.orders;

import me.bounser.nascraft.commands.Command;
import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.playerorders.PlayerOrdersManager;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class OrdersCommand extends Command {
    public OrdersCommand() {
        super("orders", new String[]{"order", "auftraege", "aufträge"}, "Player-to-player procurement orders", null);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use player orders.");
            return;
        }

        PlayerOrdersManager manager = PlayerOrdersManager.getInstance();
        if (args.length == 0 || args[0].equalsIgnoreCase("list") || args[0].equalsIgnoreCase("open")) {
            manager.open(player);
            return;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        String error;
        try {
            switch (action) {
                case "create" -> {
                    if (args.length != 4) {
                        usage(player);
                        return;
                    }
                    int amount = Integer.parseInt(args[2]);
                    double price = Double.parseDouble(args[3].replace(',', '.'));
                    error = manager.create(player, args[1], amount, price);
                }
                case "deliver" -> {
                    if (args.length < 2 || args.length > 3) {
                        usage(player);
                        return;
                    }
                    UUID id = resolveOrderId(manager, args[1]);
                    if (id == null) {
                        player.sendMessage(ChatColor.RED + "Unknown or ambiguous order id.");
                        return;
                    }
                    int amount = args.length == 3 ? Integer.parseInt(args[2]) : 0;
                    error = manager.deliver(player, id, amount);
                }
                case "collect" -> {
                    if (args.length != 2) {
                        usage(player);
                        return;
                    }
                    UUID id = resolveOrderId(manager, args[1]);
                    if (id == null) {
                        player.sendMessage(ChatColor.RED + "Unknown or ambiguous order id.");
                        return;
                    }
                    error = manager.collect(player, id);
                }
                case "cancel" -> {
                    if (args.length != 2) {
                        usage(player);
                        return;
                    }
                    UUID id = resolveOrderId(manager, args[1]);
                    if (id == null) {
                        player.sendMessage(ChatColor.RED + "Unknown or ambiguous order id.");
                        return;
                    }
                    error = manager.cancel(player, id);
                }
                default -> {
                    usage(player);
                    return;
                }
            }
        } catch (NumberFormatException exception) {
            player.sendMessage(ChatColor.RED + "Amount and price must be valid numbers.");
            return;
        }

        if (error != null) player.sendMessage(ChatColor.RED + error);
        else {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Order updated successfully.");
            manager.open(player);
        }
    }

    private UUID resolveOrderId(PlayerOrdersManager manager, String value) {
        try {
            UUID exact = UUID.fromString(value);
            return manager.getOrder(exact) == null ? null : exact;
        } catch (IllegalArgumentException ignored) {
            UUID match = null;
            String prefix = value.toLowerCase(Locale.ROOT);
            for (PlayerOrdersManager.PlayerOrder order : manager.getOrders()) {
                if (!order.id().toString().toLowerCase(Locale.ROOT).startsWith(prefix)) continue;
                if (match != null) return null;
                match = order.id();
            }
            return match;
        }
    }

    private void usage(Player player) {
        player.sendMessage(ChatColor.GOLD + "Player Orders");
        player.sendMessage(ChatColor.GRAY + "/orders" + ChatColor.WHITE + " - open the order board");
        player.sendMessage(ChatColor.GRAY + "/orders create <market-item> <amount> <price/item>");
        player.sendMessage(ChatColor.GRAY + "/orders deliver <order-id> [amount]");
        player.sendMessage(ChatColor.GRAY + "/orders collect <order-id>");
        player.sendMessage(ChatColor.GRAY + "/orders cancel <order-id>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return filter(List.of("create", "deliver", "collect", "cancel", "list"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            return filter(MarketManager.getInstance().getAllParentItems().stream().map(item -> item.getIdentifier()).toList(), args[1]);
        }
        if (args.length == 2 && List.of("deliver", "collect", "cancel").contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(PlayerOrdersManager.getInstance().getOrders().stream().map(order -> order.id().toString().substring(0, 8)).toList(), args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String prefix) {
        String normalized = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) if (value.toLowerCase(Locale.ROOT).startsWith(normalized)) result.add(value);
        return result;
    }
}
