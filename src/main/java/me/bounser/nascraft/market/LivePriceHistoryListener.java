package me.bounser.nascraft.market;

import me.bounser.nascraft.api.events.TransactionCompletedEvent;
import me.bounser.nascraft.database.DatabaseManager;
import me.bounser.nascraft.market.unit.Item;
import me.bounser.nascraft.market.unit.stats.Instant;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.time.LocalDateTime;

public final class LivePriceHistoryListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTransactionCompleted(TransactionCompletedEvent event) {
        Item item = event.getItem();
        Item parent = item.isParent() ? item : item.getParent();
        if (parent == null) return;

        DatabaseManager.get().getDatabase().saveDayPrice(
                parent,
                new Instant(LocalDateTime.now(), parent.getPrice().getValue(), Math.max(1, Math.round(event.getAmount())))
        );
        DatabaseManager.get().getDatabase().saveItem(parent);
    }
}
