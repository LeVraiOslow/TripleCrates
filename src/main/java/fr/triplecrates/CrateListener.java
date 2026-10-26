package fr.triplecrates;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CrateListener implements Listener {
    private final TripleCratesPlugin pl;
    private final Map<UUID, Long> lastClick = new HashMap<>();
    private final Map<UUID, Long> openedAt = new HashMap<>();

    public CrateListener(TripleCratesPlugin pl) { this.pl = pl; }

    private boolean throttle(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastClick.put(p.getUniqueId(), now);
        return last == null || now - last > 200;
    }

    // ---------- clics sur le bloc ----------
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null) return;
        Crate c = pl.crates().crateAt(b);
        if (c == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) open(e.getPlayer(), c, CrateManager.locKey(b));
        else if (e.getAction() == Action.LEFT_CLICK_BLOCK) preview(e.getPlayer(), c);
    }

    // ---------- clics sur les entités (modèles 3D, coffres de l'animation) ----------
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityClick(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        Entity en = e.getRightClicked();
        var pdc = en.getPersistentDataContainer();
        if (pdc.has(pl.K_SESSION, PersistentDataType.STRING)) {
            e.setCancelled(true);
            OpenSession s = OpenSession.ACTIVE.get(p.getUniqueId());
            if (e.getHand() == EquipmentSlot.HAND && s != null && throttle(p)) s.handle(en);
            return;
        }
        String loc = pdc.get(pl.K_LOC, PersistentDataType.STRING);
        if (loc == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND || !throttle(p)) return;
        Crate c = pl.crates().crateAtKey(loc);
        if (c != null) open(p, c, loc);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityAttack(PrePlayerAttackEntityEvent e) {
        Entity en = e.getAttacked();
        var pdc = en.getPersistentDataContainer();
        if (pdc.has(pl.K_SESSION, PersistentDataType.STRING)) { e.setCancelled(true); return; }
        String loc = pdc.get(pl.K_LOC, PersistentDataType.STRING);
        if (loc == null) return;
        e.setCancelled(true);
        Crate c = pl.crates().crateAtKey(loc);
        if (c != null && throttle(e.getPlayer())) preview(e.getPlayer(), c);
    }

    /** Les hitbox / modèles de caisse et les coffres d'animation ne prennent jamais de dégâts (pas de teinte rouge). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent e) {
        var pdc = e.getEntity().getPersistentDataContainer();
        if (pdc.has(pl.K_LOC, PersistentDataType.STRING) || pdc.has(pl.K_SESSION, PersistentDataType.STRING)) e.setCancelled(true);
    }

    /** Aperçu : uniquement sur la caisse, avant l'ouverture (pas pendant l'animation, pas sur les 3 coffres). */
    private void preview(Player p, Crate c) {
        if (OpenSession.ACTIVE.containsKey(p.getUniqueId())) return;
        if (!p.hasPermission("triplecrates.use")) { Text.send(p, "no-permission"); return; }
        PreviewMenu.open(p, c, 0);
    }

    // ---------- ouverture ----------
    private void open(Player p, Crate c, String locKey) {
        if (!p.hasPermission("triplecrates.use")) { Text.send(p, "no-permission"); return; }
        if (OpenSession.ACTIVE.containsKey(p.getUniqueId())) {
            // Un seul clic droit déclenche deux événements (bloc + hitbox du modèle) : le 2e arrive juste après le
            // début de l'ouverture et ne doit pas afficher de message. Seul un vrai 2e clic plus tard en affiche un.
            Long t0 = openedAt.get(p.getUniqueId());
            if (t0 == null || System.currentTimeMillis() - t0 > 500) Text.send(p, "already-opening");
            return;
        }
        if (OpenSession.BUSY.contains(locKey)) { Text.send(p, "busy"); return; }
        if (c.rewards.isEmpty()) { Text.send(p, "empty-crate"); return; }
        CrateKey key = c.keyId == null ? null : pl.crates().keys.get(c.keyId);
        if (key == null) { Text.send(p, "no-key-linked"); return; }
        int slot = findKey(p, key.id);
        if (slot < 0) { Text.send(p, "no-key", "%key%", Text.plain(Text.parse(key.name))); return; }

        PlayerInventory inv = p.getInventory();
        ItemStack s = inv.getItem(slot);
        if (s.getAmount() <= 1) inv.setItem(slot, null);
        else { s.setAmount(s.getAmount() - 1); inv.setItem(slot, s); }

        Location bl = CrateManager.parseLoc(locKey);
        if (bl == null) return;
        openedAt.put(p.getUniqueId(), System.currentTimeMillis());
        new OpenSession(pl, p, c, locKey, bl).begin();
    }

    private int findKey(Player p, String keyId) {
        PlayerInventory inv = p.getInventory();
        if (pl.getConfig().getBoolean("key-in-hand", true)) {
            int h = inv.getHeldItemSlot();
            return keyId.equals(CrateKey.idOf(inv.getItem(h))) ? h : -1;
        }
        for (int i = 0; i < inv.getSize(); i++) if (keyId.equals(CrateKey.idOf(inv.getItem(i)))) return i;
        return -1;
    }

    // ---------- pose / casse (admin) ----------
    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        ItemStack it = e.getItemInHand();
        if (!it.hasItemMeta()) return;
        String id = it.getItemMeta().getPersistentDataContainer().get(pl.K_CRATE_ITEM, PersistentDataType.STRING);
        if (id == null) return;
        Crate c = pl.crates().crates.get(id);
        if (c == null || !e.getPlayer().hasPermission("triplecrates.admin")) { e.setCancelled(true); return; }
        pl.crates().register(e.getBlockPlaced(), c);
        Text.send(e.getPlayer(), "crate-placed", "%crate%", Text.plain(Text.parse(c.name)));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        Crate c = pl.crates().crateAt(e.getBlock());
        if (c == null) return;
        Player p = e.getPlayer();
        if (!p.hasPermission("triplecrates.admin") || !p.isSneaking()) { e.setCancelled(true); return; }
        e.setDropItems(false);
        pl.crates().unregister(e.getBlock());
        e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation().add(0.5, 0.5, 0.5), pl.crates().crateItem(c));
        Text.send(p, "crate-removed");
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e) { e.blockList().removeIf(b -> pl.crates().crateAt(b) != null); }

    @EventHandler
    public void onExplode(BlockExplodeEvent e) { e.blockList().removeIf(b -> pl.crates().crateAt(b) != null); }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        for (OpenSession s : OpenSession.ACTIVE.values()) s.hideFrom(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        OpenSession s = OpenSession.ACTIVE.get(e.getPlayer().getUniqueId());
        if (s != null) s.forceFinish();
        lastClick.remove(e.getPlayer().getUniqueId());
        openedAt.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        if (!pl.crates().placed.isEmpty()) pl.later(() -> pl.crates().ensureAllVisuals());
    }
}
