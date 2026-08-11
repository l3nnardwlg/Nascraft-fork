package me.bounser.nascraft.config.lang;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.formatter.Formatter;
import me.bounser.nascraft.formatter.Separator;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.*;

public class Lang {

    private static final String COMING_SOON_MINI = "<color:#fbbf24>Coming Soon</color>";
    private static final String COMING_SOON_PLAIN = "Coming Soon";

    private YamlConfiguration lang;

    private final MiniMessage miniMessage;

    private static Lang instance;

    public static Lang get() { return instance == null ? instance = new Lang() : instance; }


    private Lang() {

        saveResourceIfNotExists("langs/en_US.yml");
        saveResourceIfNotExists("langs/es_ES.yml");

        File language = new File(Nascraft.getInstance().getDataFolder().getPath() + "/langs/" + Config.getInstance().getSelectedLanguage() + ".yml");

        if (!language.exists()) {
            Nascraft.getInstance().getLogger().severe("Lang file selected does not exist!");
            Nascraft.getInstance().getPluginLoader().disablePlugin(Nascraft.getInstance());
        }

        lang = YamlConfiguration.loadConfiguration(language);

        this.miniMessage = MiniMessage.miniMessage();
        Formatter.setSeparator(Separator.valueOf(message(Message.SEPARATOR).toUpperCase()));
    }

    public void reload() {

        File language = new File(Nascraft.getInstance().getDataFolder().getPath() + "/langs/" + Config.getInstance().getSelectedLanguage() + ".yml");

        if (!language.exists()) {
            Nascraft.getInstance().getLogger().severe("Lang file selected does not exist!");
            Nascraft.getInstance().getPluginLoader().disablePlugin(Nascraft.getInstance());
        }

        lang = YamlConfiguration.loadConfiguration(language);
        Formatter.setSeparator(Separator.valueOf(message(Message.SEPARATOR).toUpperCase()));
    }

    private void saveResourceIfNotExists(String resourcePath) {
        File resourceFile = new File(Nascraft.getInstance().getDataFolder().getPath() + "/" + resourcePath);
        if (!resourceFile.exists()) Nascraft.getInstance().saveResource(resourcePath, false);
    }

    private boolean isComingSoon(Message message) {
        String key = message.name();
        return key.startsWith("DISCORD_")
                || key.startsWith("DISCORDCMD_")
                || key.startsWith("LINK_")
                || key.startsWith("ALERT_")
                || key.startsWith("ALERTS_")
                || key.startsWith("GUI_ALERTS_")
                || key.startsWith("GUI_BUYSELL_ALERTS_")
                || key.startsWith("ANVIL_ALERT_");
    }

    private String comingSoon(Message message) {
        return message.name().startsWith("DISCORD_") ? COMING_SOON_PLAIN : COMING_SOON_MINI;
    }

    private String raw(Message message) {
        if (isComingSoon(message)) return comingSoon(message);

        String key = message.name().toLowerCase();
        if (!this.lang.contains(key)) {
            Nascraft.getInstance().getLogger().warning("Lang section not found: " + key);
            return "Lang section not found: " + key;
        }
        return this.lang.getString(key).replace("&", "§");
    }

    public void message(Player player, Message lang) {
        player.sendMessage(miniMessage.deserialize(raw(lang)));
    }

    public void message(Player player, String msg) {
        player.sendMessage(miniMessage.deserialize(msg));
    }

    public String message(Message lang) {
        return raw(lang);
    }

    public void message(Player player, Message lang, String worth, String amount, String name) {
        player.sendMessage(miniMessage.deserialize(raw(lang)
                .replace("[WORTH]", worth)
                .replace("[AMOUNT]", amount)
                .replace("[NAME]", name)));
    }

    public void message(Player player, Message lang, String placeholder, String replacement) {

        player.sendMessage(miniMessage.deserialize(raw(lang)
                .replace(placeholder, replacement)));
    }

    public void message(Player player, Message lang, String placeholder1, String replacement1, String placeholder2, String replacement2, String placeholder3, String replacement3) {

        player.sendMessage(miniMessage.deserialize(raw(lang)
                .replace(placeholder1, replacement1)
                .replace(placeholder2, replacement2)
                .replace(placeholder3, replacement3)));
    }

    public String message(Message lang, String worth, String amount, String name) {
        return raw(lang)
                .replace("[WORTH]", worth)
                .replace("[AMOUNT]", amount)
                .replace("[NAME]", name);
    }

    public String message(Message lang, String placeholder, String replacement) {
        return raw(lang)
                .replace(placeholder, replacement);
    }

    public String message(Message lang, String placeholder1, String replacement1, String placeholder2, String replacement2, String placeholder3, String replacement3) {
        return raw(lang)
                .replace(placeholder1, replacement1)
                .replace(placeholder2, replacement2)
                .replace(placeholder3, replacement3);
    }
}
