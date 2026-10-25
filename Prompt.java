package fr.triplecrates;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Saisie dans le chat pour l'éditeur (MiniMessage ou codes &). */
public final class Prompt implements Listener {
    private static final Map<UUID, Consumer<String>> PENDING = new ConcurrentHashMap<>();
    private final TripleCratesPlugin pl;

    public Prompt(TripleCratesPlugin pl) { this.pl = pl; }

    public static void ask(Player p, String text, Consumer<String> callback) {
        p.closeInventory();
        PENDING.put(p.getUniqueId(), callback);
        Text.send(p, "prompt-header", "%text%", text);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Consumer<String> cb = PENDING.remove(e.getPlayer().getUniqueId());
        if (cb == null) return;
        e.setCancelled(true);
        String s = Text.legacyToMini(PlainTextComponentSerializer.plainText().serialize(e.message()).trim());
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(pl, () -> {
            if (s.equalsIgnoreCase("cancel")) Text.send(p, "prompt-cancelled");
            else cb.accept(s);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { PENDING.remove(e.getPlayer().getUniqueId()); }
}
