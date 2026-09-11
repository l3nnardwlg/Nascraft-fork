package me.bounser.nascraft.commands.pay;

import me.bounser.nascraft.commands.Command;
import me.bounser.nascraft.config.Messages;
import me.bounser.nascraft.managers.MoneyManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PayCommand extends Command {

    private static final String PERMISSION = "nascraft.pay";
    private static final DecimalFormat AMOUNT_FORMAT = new DecimalFormat("#,##0.##");

    public PayCommand() {
        super("pay", new String[0], "Pay money directly to another player", PERMISSION);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.get().command(sender, "pay.only-player");
            return;
        }

        if (!player.hasPermission(PERMISSION)) {
            Messages.get().command(player, "pay.no-permission");
            return;
        }

        if (args.length != 2) {
            Messages.get().command(player, "pay.usage");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Messages.get().command(player, "pay.player-offline");
            return;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            Messages.get().command(player, "pay.self");
            return;
        }

        final double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException exception) {
            Messages.get().command(player, "pay.invalid-number");
            return;
        }

        if (!Double.isFinite(amount) || amount <= 0) {
            Messages.get().command(player, "pay.positive-number");
            return;
        }

        MoneyManager.TransferResult result = MoneyManager.getInstance().transfer(player, target, amount);
        switch (result) {
            case SUCCESS -> {
                String formatted = AMOUNT_FORMAT.format(amount);
                Messages.get().command(player, "pay.sent", "[PLAYER]", target.getName(), "[AMOUNT]", formatted);
                Messages.get().command(target, "pay.received", "[PLAYER]", player.getName(), "[AMOUNT]", formatted);
            }
            case INSUFFICIENT_FUNDS -> Messages.get().command(player, "pay.insufficient");
            case ECONOMY_UNAVAILABLE -> Messages.get().command(player, "pay.economy-unavailable");
            case WITHDRAW_FAILED -> Messages.get().command(player, "pay.withdraw-failed");
            case DEPOSIT_FAILED -> Messages.get().command(player, "pay.deposit-failed");
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
