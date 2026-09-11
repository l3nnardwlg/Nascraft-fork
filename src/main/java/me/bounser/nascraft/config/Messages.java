package me.bounser.nascraft.config;

import me.bounser.nascraft.Nascraft;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Configurable messages for fork-specific commands and flows.
 *
 * The original Nascraft language files remain untouched. This service covers
 * messages introduced or rewritten by the fork and automatically merges new
 * defaults into an existing messages.yml so upgrades do not require deleting
 * the server owner's customized file.
 */
public final class Messages {

    private static final String RESOURCE = "messages.yml";
    private static Messages instance;

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final File file;
    private YamlConfiguration messages;

    private Messages() {
        this.file = new File(Nascraft.getInstance().getDataFolder(), RESOURCE);
        reload();
    }

    public static Messages get() {
        if (instance == null) instance = new Messages();
        return instance;
    }

    public synchronized void reload() {
        ensureFile();
        messages = YamlConfiguration.loadConfiguration(file);
        mergeMissingDefaults();
        messages = YamlConfiguration.loadConfiguration(file);
    }

    public String text(String path, String... replacements) {
        String value = messages.getString(path);
        if (value == null) {
            Nascraft.getInstance().getLogger().warning("Message section not found: " + path);
            value = "<color:#f87171>Missing message: " + path + "</color>";
        }
        return replace(value, replacements);
    }

    public String legacy(String path, String... replacements) {
        Component component = miniMessage.deserialize(text(path, replacements));
        return LegacyComponentSerializer.legacySection().serialize(component);
    }

    public void send(CommandSender sender, String path, String... replacements) {
        sendRaw(sender, text(path, replacements));
    }

    public void command(CommandSender sender, String path, String... replacements) {
        sendRaw(sender, text("prefix") + text(path, replacements));
    }

    private void sendRaw(CommandSender sender, String raw) {
        Component component = miniMessage.deserialize(raw);
        if (sender instanceof Player player) {
            player.sendMessage(component);
        } else {
            sender.sendMessage(LegacyComponentSerializer.legacySection().serialize(component));
        }
    }

    private String replace(String value, String... replacements) {
        if (replacements == null) return value;
        if (replacements.length % 2 != 0) {
            throw new IllegalArgumentException("Message replacements must be placeholder/value pairs.");
        }
        String result = value;
        for (int i = 0; i < replacements.length; i += 2) {
            result = result.replace(replacements[i], replacements[i + 1] == null ? "" : replacements[i + 1]);
        }
        return result;
    }

    private void ensureFile() {
        if (file.isFile()) return;
        Nascraft.getInstance().saveResource(RESOURCE, false);
    }

    private void mergeMissingDefaults() {
        try (InputStream stream = Nascraft.getInstance().getResource(RESOURCE)) {
            if (stream == null) {
                Nascraft.getInstance().getLogger().warning("Bundled " + RESOURCE + " was not found; configurable fork messages cannot be updated.");
                return;
            }

            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            boolean changed = false;

            for (String key : defaults.getKeys(true)) {
                if (defaults.isConfigurationSection(key) || messages.contains(key)) continue;
                messages.set(key, defaults.get(key));
                changed = true;
            }

            if (changed) messages.save(file);
        } catch (IOException exception) {
            Nascraft.getInstance().getLogger().warning("Could not update " + RESOURCE + ": " + exception.getMessage());
        }
    }
}
