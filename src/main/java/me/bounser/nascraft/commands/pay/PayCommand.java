package me.bounser.nascraft.commands.pay;

import me.bounser.nascraft.commands.Command;
import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.managers.MoneyManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PayCommand extends Command {

    private static final DecimalFormat AMOUNT_FORMAT = new DecimalFormat("#,##0.##");

    public PayCommand() {
        super(
                "pay",
                new String[]{Config.getInstance().getCommandAlias("pay")},
                "Pay money directly to another player",
                "nascraft.pay"
        );
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use /pay.");
            return;
        }

        if (args.length != 2) {
            player.sendMessage(ChatColor.RED + "Usage: /pay <player> <amount>");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "That player is not online.");
            return;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You cannot pay yourself.");
            return;
        }

        final double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException exception) {
            player.sendMessage(ChatColor.RED + "The amount must be a valid number.");
            return;
        }

        if (!Double.isFinite(amount) || amount <= 0) {
            player.sendMessage(ChatColor.RED + "The amount must be greater than zero.");
            return;
        }

        MoneyManager.TransferResult result = MoneyManager.getInstance().transfer(player, target, amount);
        switch (result) {
            case SUCCESS -> {
                String formatted = AMOUNT_FORMAT.format(amount);
                player.sendMessage(ChatColor.GREEN + "You paid " + target.getName() + " $" + formatted + ".");
                target.sendMessage(ChatColor.GREEN + "You received $" + formatted + " from " + player.getName() + ".");
            }
            case INSUFFICIENT_FUNDS -> player.sendMessage(ChatColor.RED + "You do not have enough money.");
            case ECONOMY_UNAVAILABLE -> player.sendMessage(ChatColor.RED + "The economy provider is currently unavailable.");
            case WITHDRAW_FAILED -> player.sendMessage(ChatColor.RED + "The payment could not be withdrawn from your account.");
            case DEPOSIT_FAILED -> player.sendMessage(ChatColor.RED + "The payment failed and your money was refunded.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.getName().equalsIgnoreCase(sender.getName())) names.add(player.getName());
            }
            return StringUtil.copyPartialMatches(args[0], names, new ArrayList<>());
        }
        if (args.length == 2) return Collections.singletonList("amount");
        return Collections.emptyList();
    }
}
