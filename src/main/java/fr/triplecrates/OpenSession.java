package fr.triplecrates;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Une ouverture : explosion de la caisse -> 3 coffres tombent -> hologrammes -> le joueur choisit. */
public final class OpenSession {
    public static final Map<UUID, OpenSession> ACTIVE = new HashMap<>();
    public static final Set<String> BUSY = new HashSet<>();

    private enum Phase { EXPLODE, FALL, CHOOSE, END }

    private static final class Slot {
        ModelVisual chest;
        Location target;
        Reward reward;
        TextDisplay text;
        ItemDisplay icon;
    }

    private final TripleCratesPlugin pl;
    private final Player player;
    private final Crate crate;
    private final String locKey;       // null = ouverture admin sans caisse posée
    private final Location crateLoc;   // bloc de la caisse (ou point d'explosion si locKey == null)
    private final List<Reward> rewards;
    private final List<Slot> slots = new ArrayList<>();
    private Phase phase = Phase.EXPLODE;
    private int t = 0;
    private boolean chosen, finished;
    private BukkitTask task;
    private final float yaw;

    public OpenSession(TripleCratesPlugin pl, Player player, Crate crate, String locKey, Location crateLoc) {
        this.pl = pl;
        this.player = player;
        this.crate = crate;
        this.locKey = locKey;
        this.crateLoc = crateLoc;
        this.rewards = crate.pick(3);
        this.yaw = player.getLocation().getYaw();
    }

    private Location center() { return locKey != null ? crateLoc.clone().add(0.5, 0.5, 0.5) : crateLoc.clone(); }

    public void begin() {
        ACTIVE.put(player.getUniqueId(), this);
        if (locKey != null) {
            BUSY.add(locKey);
            ModelVisual v = pl.crates().visual(locKey);
            if (v != null) v.hideFor(pl, player);
            player.sendBlockChange(crateLoc, Material.AIR.createBlockData());
        }
        Location c = center();
        Fx.sound(player, c, crate.str("explosion-sound"), 1f, 1f);
        Fx.particle(player, c, crate.str("explosion-particle"), 2, 0.3, 0.02);
        task = Bukkit.getScheduler().runTaskTimer(pl, this::tick, 1L, 1L);
    }

    private void tick() {
        if (finished) return;
        if (!player.isOnline()) { forceFinish(); return; }
        t++;
        switch (phase) {
            case EXPLODE -> { if (t >= (int) crate.num("start-delay")) { spawnChests(); phase = Phase.FALL; t = 0; } }
            case FALL -> {
                int ticks = Math.max(1, (int) crate.num("fall-ticks"));
                double p = Math.min(1.0, t / (double) ticks), e = p * p, h = crate.num("fall-height");
                for (Slot s : slots) {
                    Location l = s.target.clone().add(0, h * (1 - e), 0);
                    l.setYaw(yaw);
                    s.chest.moveTo(l);
                    Fx.particle(player, l.clone().add(0, 0.6, 0), crate.str("trail-particle"), 2, 0.2, 0.01);
                }
                if (t >= ticks) land();
            }
            case CHOOSE -> {
                if (t >= (int) (crate.num("choose-timeout") * 20)) {
                    Text.send(player, "timeout");
                    choose(ThreadLocalRandom.current().nextInt(slots.size()));
                }
            }
            case END -> { if (t >= (int) crate.num("end-delay")) finish(); }
        }
    }

    private void spawnChests() {
        Vector f = player.getLocation().getDirection().setY(0);
        if (f.lengthSquared() < 0.0001) f = new Vector(0, 0, 1);
        f.normalize();
        Vector right = new Vector(-f.getZ(), 0, f.getX());
        double dist = crate.num("distance"), spacing = crate.num("spacing"), h = crate.num("fall-height");
        Location base = player.getLocation().clone().add(f.clone().multiply(dist));

        Hooks.ModelSpec spec = new Hooks.ModelSpec();
        spec.type = crate.str("chest-model-type").toUpperCase();
        spec.id = crate.str("chest-model-id");
        spec.width = 1.2;
        spec.height = 1.2;
        ItemStack chestItem = ItemUtil.parse(crate.str("chest-item"), 1);

        for (int i = 0; i < rewards.size(); i++) {
            Slot s = new Slot();
            s.reward = rewards.get(i);
            s.target = base.clone().add(right.clone().multiply((i - (rewards.size() - 1) / 2.0) * spacing));
            Location top = s.target.clone().add(0, h, 0);
            top.setYaw(yaw);
            s.chest = ModelVisual.spawn(top, spec, chestItem, pl.K_SESSION, player.getUniqueId() + ":" + i, player);
            slots.add(s);
        }
    }

    private void land() {
        for (Slot s : slots) {
            Fx.sound(player, s.target, crate.str("land-sound"), 0.8f, 1f);
            Fx.particle(player, s.target.clone().add(0, 0.3, 0), crate.str("land-particle"), 25, 0.5, 0.05);
            spawnHologram(s);
        }
        phase = Phase.CHOOSE;
        t = 0;
        Text.send(player, "choose");
    }

    private void spawnHologram(Slot s) {
        var cfg = pl.getConfig();
        World w = s.target.getWorld();
        float scale = (float) cfg.getDouble("hologram.item-scale", 1.4);
        Location textLoc = s.target.clone().add(0, cfg.getDouble("hologram.text-height", 1.55), 0);
        Location itemLoc = s.target.clone().add(0, cfg.getDouble("hologram.item-height", 2.4), 0);

        s.text = only(w.spawn(textLoc, TextDisplay.class, d -> {
            d.setVisibleByDefault(false);
            d.text(holoText(s.reward));
            d.setBillboard(Display.Billboard.CENTER);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setPersistent(false);
        }));
        s.icon = only(w.spawn(itemLoc, ItemDisplay.class, d -> {
            d.setVisibleByDefault(false);
            d.setItemStack(s.reward.displayItem());
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setPersistent(false);
            d.setTransformation(scaled(0.01f));
        }));
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (s.icon != null && s.icon.isValid()) {
                s.icon.setInterpolationDelay(0);
                s.icon.setInterpolationDuration(10);
                s.icon.setTransformation(scaled(scale));
            }
        }, 1L);
    }

    /** L'entité n'est visible que par le joueur qui ouvre la caisse (moins de paquets / de lag). */
    private <T extends Entity> T only(T e) {
        player.showEntity(pl, e);
        return e;
    }

    private static Transformation scaled(float s) {
        return new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(s, s, s), new AxisAngle4f());
    }

    private Component holoText(Reward r) {
        TagResolver tr = TagResolver.resolver(
                Placeholder.component("name", r.nameComponent()),
                Placeholder.parsed("rarity", pl.crates().rarityName(r.rarity)),
                Placeholder.parsed("chance", Text.num(crate.chance(r))));
        List<Component> lines = new ArrayList<>();
        for (String l : pl.getConfig().getStringList("hologram.lines")) lines.add(MiniMessage.miniMessage().deserialize(l, tr));
        return Component.join(JoinConfiguration.newlines(), lines);
    }

    /** Un joueur se connecte pendant l'animation : il ne doit pas voir les modèles 3D des coffres. */
    public void hideFrom(Player other) {
        if (other.getUniqueId().equals(player.getUniqueId())) return;
        for (Slot s : slots) if (s.chest != null) s.chest.hideModelFor(other);
    }

    /** Appelé par le listener quand le joueur clique sur une entité de la session. */
    public boolean handle(Entity clicked) {
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).chest.owns(clicked)) {
                if (phase == Phase.CHOOSE && !chosen) choose(i);
                return true;
            }
        }
        return false;
    }

    private void choose(int index) {
        if (chosen) return;
        chosen = true;
        Slot s = slots.get(index);
        Location at = s.target.clone().add(0, 1, 0);
        Fx.particle(player, at, crate.str("win-particle"), 40, 0.6, 0.1);
        Fx.sound(player, at, crate.str("win-sound"), 1f, 1f);
        s.reward.give(player, crate);
        Text.send(player, "chosen", "%reward%", Text.plain(s.reward.nameComponent()), "%rarity%", pl.crates().rarityName(s.reward.rarity));
        phase = Phase.END;
        t = 0;
    }

    /** Joueur déconnecté / plugin désactivé : donne une récompense aléatoire parmi les 3 si rien n'a été choisi. */
    public void forceFinish() {
        if (finished) return;
        if (!chosen && !rewards.isEmpty()) {
            chosen = true;
            rewards.get(ThreadLocalRandom.current().nextInt(rewards.size())).give(player, crate);
        }
        finish();
    }

    private void finish() {
        if (finished) return;
        finished = true;
        if (task != null) task.cancel();
        for (Slot s : slots) {
            if (s.chest != null) s.chest.remove();
            if (s.text != null) s.text.remove();
            if (s.icon != null) s.icon.remove();
        }
        if (locKey != null) {
            if (player.isOnline()) player.sendBlockChange(crateLoc, crateLoc.getBlock().getBlockData());
            BUSY.remove(locKey);
            ModelVisual v = pl.crates().visual(locKey);
            if (v != null && v.valid()) v.showFor(pl, player); else pl.crates().ensureVisual(locKey);
        }
        ACTIVE.remove(player.getUniqueId());
    }
}
