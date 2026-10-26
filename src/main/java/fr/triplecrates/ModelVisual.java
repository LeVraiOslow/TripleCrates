package fr.triplecrates;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/** Un "coffre" visuel : hitbox cliquable (Interaction) + modèle (ModelEngine / MythicMobs) ou item display. */
public final class ModelVisual {
    private Interaction hitbox;
    private ItemDisplay display;
    private Entity extra;
    private double yOffset;
    private boolean modelOk;

    public static ModelVisual spawn(Location loc, Hooks.ModelSpec model, ItemStack chestItem, NamespacedKey tagKey, String tagVal, Player onlyFor) {
        TripleCratesPlugin plugin = TripleCratesPlugin.get();
        ModelVisual v = new ModelVisual();
        World w = loc.getWorld();
        float width = (float) (model == null ? 1.2 : model.width), height = (float) (model == null ? 1.2 : model.height);
        v.hitbox = w.spawn(loc, Interaction.class, i -> {
            i.setInteractionWidth(width);
            i.setInteractionHeight(height);
            i.setResponsive(true);
            i.setPersistent(false);
            if (onlyFor != null) i.setVisibleByDefault(false);
            i.getPersistentDataContainer().set(tagKey, PersistentDataType.STRING, tagVal);
        });
        if (onlyFor != null) onlyFor.showEntity(plugin, v.hitbox);
        if (model != null && model.active()) {
            v.yOffset = model.yOffset;
            Location ml = loc.clone().add(0, model.yOffset, 0);
            if (model.type.equalsIgnoreCase("MODELENGINE")) {
                v.modelOk = Hooks.attachModelEngine(v.hitbox, model.id);
            } else if (model.type.equalsIgnoreCase("MYTHICMOBS")) {
                Entity e = Hooks.spawnMythic(model.id, ml);
                if (e != null) {
                    e.setPersistent(false);
                    e.setInvulnerable(true);
                    if (e instanceof LivingEntity le) le.setAI(false);
                    e.getPersistentDataContainer().set(tagKey, PersistentDataType.STRING, tagVal);
                    if (onlyFor != null) { e.setVisibleByDefault(false); onlyFor.showEntity(plugin, e); }
                    v.extra = e;
                    v.modelOk = true;
                }
            }
            if (onlyFor != null) v.restrictTo(onlyFor);
            // MythicMobs peut appliquer son modèle avec un léger délai : on re-applique juste après
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!v.valid()) return;
                Hooks.lockModel(v.hitbox);
                Hooks.lockModel(v.extra);
                if (onlyFor != null) v.restrictTo(onlyFor);
            }, 2L);
        } else if (chestItem != null) {
            v.display = w.spawn(loc, ItemDisplay.class, d -> {
                d.setItemStack(chestItem);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setBillboard(Display.Billboard.FIXED);
                d.setPersistent(false);
                if (onlyFor != null) d.setVisibleByDefault(false);
                d.setTeleportDuration(1);
                d.setTransformation(new Transformation(new Vector3f(0, 0.55f, 0), new AxisAngle4f(), new Vector3f(1.1f, 1.1f, 1.1f), new AxisAngle4f()));
            });
            if (onlyFor != null) onlyFor.showEntity(plugin, v.display);
        }
        return v;
    }

    /** Cache le modèle à un seul joueur (les autres le voient toujours). */
    public void hideFor(TripleCratesPlugin pl, Player p) {
        for (Entity e : new Entity[]{hitbox, display, extra}) if (e != null && e.isValid()) p.hideEntity(pl, e);
        hideModelFor(p);
    }

    public void showFor(TripleCratesPlugin pl, Player p) {
        for (Entity e : new Entity[]{hitbox, display, extra}) if (e != null && e.isValid()) p.showEntity(pl, e);
        Hooks.setModelVisible(hitbox, p, true);
        Hooks.setModelVisible(extra, p, true);
    }

    /** Cache le modèle ModelEngine (hitbox ou mob MythicMobs) à ce joueur. */
    public void hideModelFor(Player p) {
        Hooks.setModelVisible(hitbox, p, false);
        Hooks.setModelVisible(extra, p, false);
    }

    /** Le modèle n'est visible que par {@code owner} : tous les autres joueurs en ligne sont masqués. */
    public void restrictTo(Player owner) {
        for (Player o : owner.getServer().getOnlinePlayers()) if (!o.getUniqueId().equals(owner.getUniqueId())) hideModelFor(o);
    }

    public boolean valid() { return hitbox != null && hitbox.isValid(); }

    /** Le modèle 3D a bien été appliqué. */
    public boolean hasModel() { return modelOk; }

    public boolean owns(Entity e) {
        return e != null && ((hitbox != null && e.getUniqueId().equals(hitbox.getUniqueId()))
                || (extra != null && e.getUniqueId().equals(extra.getUniqueId()))
                || (display != null && e.getUniqueId().equals(display.getUniqueId())));
    }

    public void moveTo(Location l) {
        hitbox.teleport(l);
        if (display != null) display.teleport(l);
        if (extra != null && extra.isValid()) extra.teleport(l.clone().add(0, yOffset, 0));
    }

    public void remove() {
        if (hitbox != null) hitbox.remove();
        if (display != null) display.remove();
        if (extra != null) extra.remove();
    }
}
