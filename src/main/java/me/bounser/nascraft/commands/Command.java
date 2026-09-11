package me.bounser.nascraft.commands;

import me.bounser.nascraft.Nascraft;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public abstract class Command extends BukkitCommand {


    protected Command(String command, String[] aliases, String description, String permission) {
        super(command);

        this.setAliases(Arrays.asList(aliases));
        this.setDescription(description);
        this.setPermission(permission);

        if (!isCustomFeatureEnabled(command)) {
            Nascraft.getInstance().getLogger().info("Custom feature command /" + command + " is disabled in config.yml.");
            return;
        }

        try {
            Field field = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            field.setAccessible(true);
            CommandMap map = (CommandMap) field.get(Bukkit.getServer());

            map.register(command, this);

        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isCustomFeatureEnabled(String command) {
        if (Nascraft.getInstance() == null || Nascraft.getInstance().getConfig() == null) return true;

        return switch (command.toLowerCase(Locale.ROOT)) {
            case "pay" -> Nascraft.getInstance().getConfig().getBoolean("custom-features.pay", true);
            case "orders", "order" -> Nascraft.getInstance().getConfig().getBoolean("custom-features.orders", true);
            case "auction", "auctionhouse", "auction-house", "ah" ->
                    Nascraft.getInstance().getConfig().getBoolean("custom-features.auction-house", true);
            default -> true;
        };
    }

    @Override
    public boolean execute(@NotNull CommandSender commandSender, @NotNull String s, @NotNull String[] strings) {
        execute(commandSender, strings);
        return false;
    }

    public abstract void execute(CommandSender sender, String[] args);

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
        return onTabComplete(sender, args);
    }

    public abstract List<String> onTabComplete(CommandSender sender, String[] args);
}
