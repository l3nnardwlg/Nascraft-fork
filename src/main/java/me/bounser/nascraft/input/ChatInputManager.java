package me.bounser.nascraft.input;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.config.Messages;
import me.bounser.nascraft.scheduler.FoliaScheduler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Version-independent one-shot text input for editor/search flows. */
public final class ChatInputManager implements Listener {

    private static final String CANCEL_COMMAND = "/nascraftcancel";
    private static final ChatInputManager INSTANCE = new ChatInputManager();
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    private ChatInputManager() {}

    public static ChatInputManager getInstance() { return INSTANCE; }

    public void request(Player player, String prompt, Consumer<String> handler, Runnable onCancel) {
        sessions.put(player.getUniqueId(), new Session(handler, onCancel));
        player.closeInventory();
        Messages.get().send(player, "input.prompt", "[PROMPT]", prompt);
        Messages.get().send(player, "input.cancel-hint");
    }

    public void clear(Player player) {
        sessions.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Session session = sessions.remove(event.getPlayer().getUniqueId());
        if (session == null) return;

        event.setCancelled(true);
        String value = event.getMessage().trim();
        FoliaScheduler.runAtEntity(Nascraft.getInstance(), event.getPlayer(), () -> {
            if (value.toLowerCase(Locale.ROOT).equals("cancel")) {
                cancel(event.getPlayer(), session);
                return;
            }
            session.handler().accept(value);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String command = event.getMessage().trim();
        if (!command.equalsIgnoreCase(CANCEL_COMMAND) && !command.equalsIgnoreCase("/cancel")) return;

        Session session = sessions.remove(event.getPlayer().getUniqueId());
        if (session == null) return;

        event.setCancelled(true);
        FoliaScheduler.runAtEntity(Nascraft.getInstance(), event.getPlayer(), () -> cancel(event.getPlayer(), session));
    }

    private void cancel(Player player, Session session) {
        Messages.get().send(player, "input.cancelled");
        if (session.onCancel() != null) session.onCancel().run();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    private record Session(Consumer<String> handler, Runnable onCancel) {}
}
