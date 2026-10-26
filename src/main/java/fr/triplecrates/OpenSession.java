package fr.triplecrates;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.Directional;
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

/**
 * Une ouverture :
 * la caisse gonfle puis explose -> 3 coffres tombent et rebondissent -> machine à sous sur chaque coffre
 * -> la récompense s'arrête (éclats de la couleur de la rareté) -> le joueur choisit son coffre.
 * Tout est envoyé uniquement au joueur qui ouvre.
 */
public final class OpenSession {
    public static final Map<UUID, OpenSession> ACTIVE = new HashMap<>();
    public static final Set<String> BUSY = new HashSet<>();

    private enum Phase { INFLATE, PAUSE, FALL, SPIN, CHOOSE, END }

    /** Rebonds après l'impact : durée (ticks) et hauteur relative de chaque rebond. */
    private static final int[] BOUNCE_TICKS = {8, 5, 3};
    private static final double[] BOUNCE_MULT = {1.0, 0.4, 0.15};

    private static final class Slot {
        ModelVisual chest;
        Location target;
        float yaw;
        Reward reward;
        TextDisplay text;
        ItemDisplay icon;
        // machine à sous
        List<Integer> times = List.of();
        List<Reward> seq = List.of();
        int step;
        boolean stopped;
    }

    private static final class Shard {
        double x, y, z, vx, vy, vz;
        int life;
        Color color;
        float size;
    }

    private final TripleCratesPlugin pl;
    private final Player player;
    private final Crate crate;
    private final String locKey;       // null = ouverture admin sans caisse posée
    private final Location crateLoc;   // bloc de la caisse (ou point d'explosion si locKey == null)
    private final List<Reward> rewards;
    private final List<Slot> slots = new ArrayList<>();
    private final List<Shard> shards = new ArrayList<>();
    private Phase phase = Phase.INFLATE;
    private int t = 0, stoppedAt = -1;
    private boolean chosen, finished;
    private BukkitTask task;
    private final float yaw;
    private final float yawOffset;
    private ItemDisplay crateDisplay;

    public OpenSession(TripleCratesPlugin pl, Player player, Crate crate, String locKey, Location crateLoc) {
        this.pl = pl;
        this.player = player;
        this.crate = crate;
        this.locKey = locKey;
        this.crateLoc = crateLoc;
        this.rewards = crate.pick(3);
        this.yaw = player.getLocation().getYaw();
        this.yawOffset = (float) crate.num("chest-yaw-offset", 0);
    }

    private Location center() { return locKey != null ? crateLoc.clone().add(0.5, 0.5, 0.5) : crateLoc.clone(); }

    private static float yawOf(Vector v) { return (float) Math.toDegrees(Math.atan2(-v.getX(), v.getZ())); }

    // =====================================================================================================
    // Démarrage
    // =====================================================================================================
    public void begin() {
        ACTIVE.put(player.getUniqueId(), this);
        boolean hasModel = crate.model.active();
        if (locKey != null) {
            BUSY.add(locKey);
            // caisse "bloc" : on la remplace par un faux modèle qui gonfle (visible uniquement par le joueur)
            if (!hasModel) player.sendBlockChange(crateLoc, Material.AIR.createBlockData());
        }
        if (locKey == null || !hasModel) spawnCrateDisplay();
        Fx.sound(player, center(), crate.str("inflate-start-sound"), 1f, 1f);
        task = Bukkit.getScheduler().runTaskTimer(pl, this::tick, 1L, 1L);
    }

    private void spawnCrateDisplay() {
        Material m = crate.block.isItem() ? crate.block : Material.CHEST;
        float cy = yaw + yawOffset;
        if (locKey != null && crateLoc.getBlock().getBlockData() instanceof Directional d) {
            cy = yawOf(d.getFacing().getDirection().multiply(-1)) + yawOffset;
        }
        Location at = center();
        at.setYaw(cy);
        at.setPitch(0);
        crateDisplay = only(at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setVisibleByDefault(false);
            d.setItemStack(new ItemStack(m));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(Display.Billboard.FIXED);
            d.setPersistent(false);
            d.setTransformation(crateTransform(1f, 0f, 0f, 0f));
        }));
    }

    private static Transformation crateTransform(float s, float jx, float jz, float roll) {
        return new Transformation(new Vector3f(jx, (s - 1f) * 0.5f, jz), new AxisAngle4f(roll, 0, 0, 1),
                new Vector3f(s, s, s), new AxisAngle4f());
    }

    // =====================================================================================================
    // Boucle principale
    // =====================================================================================================
    private void tick() {
        if (finished) return;
        if (!player.isOnline()) { forceFinish(); return; }
        t++;
        tickShards();
        switch (phase) {
            case INFLATE -> {
                int total = Math.max(5, (int) crate.num("inflate-ticks", 36));
                inflate(Math.min(1.0, t / (double) total));
                if (t >= total) { explode(); phase = Phase.PAUSE; t = 0; }
            }
            case PAUSE -> { if (t >= (int) crate.num("start-delay", 6)) { spawnChests(); phase = Phase.FALL; t = 0; } }
            case FALL -> fall();
            case SPIN -> spin();
            case CHOOSE -> {
                if (t >= (int) (crate.num("choose-timeout") * 20)) {
                    Text.send(player, "timeout");
                    choose(ThreadLocalRandom.current().nextInt(slots.size()));
                }
            }
            case END -> { if (t >= (int) crate.num("end-delay")) finish(); }
        }
    }

    // =====================================================================================================
    // 1) La caisse gonfle puis explose
    // =====================================================================================================
    private void inflate(double p) {
        double max = crate.num("inflate-scale", 2.0);
        if (max < 1.01) max = 1.0;
        float s = (float) (1 + (max - 1) * Math.pow(p, 1.6));      // la pression monte de plus en plus vite
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double amp = 0.01 + 0.06 * p;                                // et la caisse tremble de plus en plus
        float jx = (float) ((r.nextDouble() - 0.5) * 2 * amp), jz = (float) ((r.nextDouble() - 0.5) * 2 * amp);
        float roll = (float) (Math.sin(t * 1.7) * 0.07 * p);
        if (crateDisplay != null && crateDisplay.isValid()) {
            crateDisplay.setInterpolationDelay(0);
            crateDisplay.setInterpolationDuration(2);
            crateDisplay.setTransformation(crateTransform(s, jx, jz, roll));
        }
        Location c = center().add(0, (s - 1) * 0.5, 0);
        if (t % 3 == 0) Fx.sound(player, c, crate.str("inflate-sound"), 0.7f, (float) (0.5 + 1.5 * p));
        Fx.particle(player, c, crate.str("inflate-particle"), 2, 0.3 * s, 0.01);
    }

    private void explode() {
        if (crateDisplay != null) { crateDisplay.remove(); crateDisplay = null; }
        if (locKey != null && crate.model.active()) {
            ModelVisual v = pl.crates().visual(locKey);
            if (v != null) v.hideFor(pl, player);
            player.sendBlockChange(crateLoc, Material.AIR.createBlockData());
        }
        Location c = center();
        Fx.sound(player, c, crate.str("explosion-sound"), 1f, 1f);
        Fx.particle(player, c, crate.str("explosion-particle"), 2, 0.3, 0.02);
    }

    // =====================================================================================================
    // 2) Les 3 coffres tombent, rebondissent et se stabilisent
    // =====================================================================================================
    private void spawnChests() {
        Vector f = player.getLocation().getDirection().setY(0);
        if (f.lengthSquared() < 0.0001) f = new Vector(0, 0, 1);
        f.normalize();
        Vector right = new Vector(-f.getZ(), 0, f.getX());
        double dist = crate.num("distance"), spacing = crate.num("spacing"), h = crate.num("fall-height");
        double turn = Math.max(0, Math.min(1, crate.num("side-turn", 0.5)));
        Location pLoc = player.getLocation();
        Location base = pLoc.clone().add(f.clone().multiply(dist));
        base.setPitch(0);   // sinon les coffres se penchent quand le joueur regarde vers le bas

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

            // coffres de gauche / droite : légèrement tournés vers le joueur (le central reste face à lui)
            Vector toPlayer = new Vector(pLoc.getX() - s.target.getX(), 0, pLoc.getZ() - s.target.getZ());
            float delta = 0f;
            if (toPlayer.lengthSquared() > 0.0001) {
                float full = yawOf(toPlayer.normalize().multiply(-1));
                delta = ((full - yaw + 540f) % 360f) - 180f;
            }
            s.yaw = yaw + (float) (turn * delta) + yawOffset;

            Location top = s.target.clone().add(0, h, 0);
            top.setYaw(s.yaw);
            top.setPitch(0);
            s.chest = ModelVisual.spawn(top, spec, chestItem, pl.K_SESSION, player.getUniqueId() + ":" + i, player);
            slots.add(s);
        }
    }

    private void fall() {
        int F = Math.max(1, (int) crate.num("fall-ticks", 16));
        double h = crate.num("fall-height"), H = crate.num("bounce-height", 0.45);
        boolean bounce = H > 0.01;
        int bounceTotal = 0;
        for (int d : BOUNCE_TICKS) bounceTotal += d;
        int total = bounce ? F + bounceTotal : F;

        double off;
        if (t <= F) {
            double p = t / (double) F;
            off = h * (1 - p * p);                  // accélération constante : attirés très fort vers le sol
        } else {
            off = 0;
            int u = t - F;
            for (int k = 0; k < BOUNCE_TICKS.length; k++) {
                if (u <= BOUNCE_TICKS[k]) {
                    double x = u / (double) BOUNCE_TICKS[k];
                    off = H * BOUNCE_MULT[k] * 4 * x * (1 - x);
                    break;
                }
                u -= BOUNCE_TICKS[k];
            }
        }

        for (Slot s : slots) {
            Location l = s.target.clone().add(0, off, 0);
            l.setYaw(s.yaw);
            l.setPitch(0);
            s.chest.moveTo(l);
            if (t < F) Fx.particle(player, l.clone().add(0, 0.6, 0), crate.str("trail-particle"), 2, 0.2, 0.01);
        }

        if (t == F) impact(1f, 1f, true);
        else if (bounce) {
            int c1 = F + BOUNCE_TICKS[0], c2 = c1 + BOUNCE_TICKS[1], c3 = c2 + BOUNCE_TICKS[2];
            if (t == c1) impact(0.5f, 1.2f, false);
            else if (t == c2) impact(0.3f, 1.4f, false);
            else if (t == c3) impact(0.2f, 1.6f, false);
        }
        if (t >= total) startSpin();
    }

    private void impact(float vol, float pitch, boolean first) {
        for (Slot s : slots) {
            Fx.sound(player, s.target, crate.str("land-sound"), vol, pitch);
            if (first) Fx.particle(player, s.target.clone().add(0, 0.3, 0), crate.str("land-particle"), 25, 0.5, 0.05);
            else Fx.particle(player, s.target.clone().add(0, 0.1, 0), crate.str("land-particle"), 4, 0.35, 0.02);
        }
    }

    // =====================================================================================================
    // 3) Machine à sous : les récompenses défilent, puis chaque coffre s'arrête sur la sienne
    // =====================================================================================================
    private void startSpin() {
        phase = Phase.SPIN;
        t = 0;
        stoppedAt = -1;
        int base = Math.max(10, (int) crate.num("spin-ticks", 50));
        int stagger = Math.max(0, (int) crate.num("spin-stagger", 14));
        List<Reward> pool = new ArrayList<>(crate.rewards.values());
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        for (int i = 0; i < slots.size(); i++) {
            Slot s = slots.get(i);
            s.times = schedule(base + i * stagger);
            int n = s.times.size();
            List<Reward> seq = new ArrayList<>();
            Reward prev = null;
            for (int k = 0; k < n - 1; k++) {
                Reward r;
                int guard = 0;
                do { r = pool.get(rnd.nextInt(pool.size())); }
                while (pool.size() > 1 && (r == prev || (k == n - 2 && r == s.reward)) && guard++ < 20);
                seq.add(r);
                prev = r;
            }
            seq.add(s.reward);
            s.seq = seq;
            spawnHologram(s, seq.get(0));
        }
    }

    /** Instants (ticks) où le défilement change : de plus en plus espacés, le dernier tombe pile sur {@code total}. */
    private static List<Integer> schedule(int total) {
        List<Integer> times = new ArrayList<>();
        double elapsed = 0, gap = 1;
        int prev = 0;
        while (true) {
            elapsed += gap;
            gap = gap * 1.18 + 0.1;
            int tk = (int) Math.round(elapsed);
            if (tk <= prev) tk = prev + 1;
            if (tk >= total - 2) break;
            times.add(tk);
            prev = tk;
        }
        times.add(total);
        return times;
    }

    private void spin() {
        boolean all = true;
        for (Slot s : slots) {
            if (s.stopped) continue;
            if (s.step < s.times.size() && t >= s.times.get(s.step)) {
                Reward r = s.seq.get(s.step);
                s.step++;
                if (s.step >= s.times.size()) stopSlot(s);
                else showSpin(s, r);
            }
            if (!s.stopped) all = false;
        }
        if (!all) return;
        if (stoppedAt < 0) stoppedAt = t;
        if (t - stoppedAt >= (int) crate.num("reveal-pause", 12)) {
            phase = Phase.CHOOSE;
            t = 0;
            Text.send(player, "choose");
        }
    }

    private void showSpin(Slot s, Reward r) {
        if (s.icon != null && s.icon.isValid()) s.icon.setItemStack(r.displayItem());
        if (s.text != null && s.text.isValid()) s.text.text(spinText(r));
        float prog = s.step / (float) s.times.size();
        Fx.sound(player, s.target, crate.str("spin-sound"), 0.5f, 0.8f + 1.0f * prog);
    }

    private void stopSlot(Slot s) {
        s.stopped = true;
        if (s.icon != null && s.icon.isValid()) {
            s.icon.setItemStack(s.reward.displayItem());
            pop(s);
        }
        if (s.text != null && s.text.isValid()) s.text.text(holoText(s.reward));
        int rank = Math.max(0, new ArrayList<>(pl.crates().rarities.keySet()).indexOf(s.reward.rarity));
        Fx.sound(player, s.target, crate.str("spin-stop-sound"), 1f, Math.min(2f, 0.8f + 0.3f * rank));
        burst(s);
    }

    /** Petit "pop" de l'icône quand la roue s'arrête. */
    private void pop(Slot s) {
        float scale = (float) pl.getConfig().getDouble("hologram.item-scale", 1.4);
        s.icon.setInterpolationDelay(0);
        s.icon.setInterpolationDuration(3);
        s.icon.setTransformation(scaled(scale * 1.6f));
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!finished && s.icon != null && s.icon.isValid()) {
                s.icon.setInterpolationDelay(0);
                s.icon.setInterpolationDuration(6);
                s.icon.setTransformation(scaled(scale));
            }
        }, 4L);
    }

    // ---------- éclats de la couleur de la rareté ----------
    private Color rarityColor(String rarity) {
        String hex = pl.getConfig().getString("rarity-colors." + rarity, "#FFFFFF");
        try { return Color.fromRGB(Integer.parseInt(hex.replace("#", "").trim(), 16) & 0xFFFFFF); }
        catch (NumberFormatException e) { return Color.WHITE; }
    }

    private void burst(Slot s) {
        Color col = rarityColor(s.reward.rarity);
        Color light = Color.fromRGB((col.getRed() + 255) / 2, (col.getGreen() + 255) / 2, (col.getBlue() + 255) / 2);
        int n = Math.max(0, pl.getConfig().getInt("burst-count", 28));
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location o = s.target.clone().add(0, 0.9, 0);
        for (int i = 0; i < n; i++) {
            Shard sh = new Shard();
            sh.x = o.getX(); sh.y = o.getY(); sh.z = o.getZ();
            double a = r.nextDouble() * Math.PI * 2, sp = 0.04 + r.nextDouble() * 0.16;
            sh.vx = Math.cos(a) * sp;
            sh.vz = Math.sin(a) * sp;
            sh.vy = 0.25 + r.nextDouble() * 0.3;
            sh.life = 12 + r.nextInt(9);
            sh.color = r.nextInt(100) < 30 ? light : col;
            sh.size = 1.0f + r.nextFloat() * 0.7f;
            shards.add(sh);
        }
    }

    private void tickShards() {
        if (shards.isEmpty()) return;
        World w = player.getWorld();
        for (Iterator<Shard> it = shards.iterator(); it.hasNext(); ) {
            Shard sh = it.next();
            sh.x += sh.vx; sh.y += sh.vy; sh.z += sh.vz;
            sh.vy -= 0.04;
            sh.vx *= 0.96; sh.vz *= 0.96;
            player.spawnParticle(Particle.DUST, new Location(w, sh.x, sh.y, sh.z), 1, 0, 0, 0, 0,
                    new Particle.DustOptions(sh.color, sh.size));
            if (--sh.life <= 0) it.remove();
        }
    }

    // =====================================================================================================
    // Hologrammes
    // =====================================================================================================
    private void spawnHologram(Slot s, Reward first) {
        var cfg = pl.getConfig();
        World w = s.target.getWorld();
        float scale = (float) cfg.getDouble("hologram.item-scale", 1.4);
        Location textLoc = s.target.clone().add(0, cfg.getDouble("hologram.text-height", 1.55), 0);
        Location itemLoc = s.target.clone().add(0, cfg.getDouble("hologram.item-height", 2.4), 0);

        s.text = only(w.spawn(textLoc, TextDisplay.class, d -> {
            d.setVisibleByDefault(false);
            d.text(spinText(first));
            d.setBillboard(Display.Billboard.CENTER);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setPersistent(false);
        }));
        s.icon = only(w.spawn(itemLoc, ItemDisplay.class, d -> {
            d.setVisibleByDefault(false);
            d.setItemStack(first.displayItem());
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setPersistent(false);
            d.setTransformation(scaled(0.01f));
        }));
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!finished && s.icon != null && s.icon.isValid()) {
                s.icon.setInterpolationDelay(0);
                s.icon.setInterpolationDuration(6);
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

    private TagResolver resolver(Reward r) {
        Component name = r.nameComponent();
        return TagResolver.resolver(
                Placeholder.component("name", name),
                Placeholder.component("n", name),
                Placeholder.parsed("rarity", pl.crates().rarityName(r.rarity)),
                Placeholder.parsed("chance", Text.num(crate.chance(r))));
    }

    private Component join(List<String> lines, Reward r) {
        TagResolver tr = resolver(r);
        List<Component> out = new ArrayList<>();
        for (String l : lines) out.add(MiniMessage.miniMessage().deserialize(l, tr));
        return Component.join(JoinConfiguration.newlines(), out);
    }

    /** Hologramme final (nom + rareté). */
    private Component holoText(Reward r) { return join(pl.getConfig().getStringList("hologram.lines"), r); }

    /** Hologramme pendant le défilement : le nom seulement, la rareté reste cachée jusqu'à l'arrêt. */
    private Component spinText(Reward r) {
        List<String> l = pl.getConfig().getStringList("hologram.spin-lines");
        if (l.isEmpty()) l = List.of("<name>", "<gray>???");
        return join(l, r);
    }

    // =====================================================================================================
    // Choix / fin
    // =====================================================================================================
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
        if (crateDisplay != null) crateDisplay.remove();
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
