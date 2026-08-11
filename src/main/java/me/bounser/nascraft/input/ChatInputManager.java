package me.bounser.nascraft.input;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.scheduler.FoliaScheduler;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Version-independent one-shot text input for editor/search flows. */
public final class ChatInputManager implements Listener {

    private static final ChatInputManager INSTANCE = new ChatInputManager();
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    private ChatInputManager() {}

    public static ChatInputManager getInstance() { return INSTANCE; }

    public void request(Player player, String prompt, Consumer<String> handler, Runnable onCancel) {
        sessions.put(player.getUniqueId(), new Session(handler, onCancel));
        player.closeInventory();
        player.sendMessage(ChatColor.GOLD + prompt);
        player.sendMessage(ChatColor.GRAY + "Type " + ChatColor.YELLOW + "cancel" + ChatColor.GRAY + " to return without changing anything.");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Session session = sessions.remove(event.getPlayer().getUniqueId());
        if (session == null) return;

        event.setCancelled(true);
        String value = event.getMessage().trim();
        FoliaScheduler.runAtEntity(Nascraft.getInstance(), event.getPlayer(), () -> {
            if (value.toLowerCase(Locale.ROOT).equals("cancel")) {
                if (session.onCancel() != null) session.onCancel().run();
                return;
            }
            session.handler().accept(value);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    private record Session(Consumer<String> handler, Runnable onCancel) {}
}
