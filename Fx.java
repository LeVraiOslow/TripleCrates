package fr.triplecrates;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.entity.Player;

import java.util.Locale;

final class Fx {
    private Fx() {}

    /** viewer != null : particules envoyées uniquement à ce joueur (pas de diffusion au monde). */
    static void particle(Player viewer, Location l, String name, int count, double spread, double speed) {
        if (name == null || name.isBlank()) return;
        try {
            Particle p = Registry.PARTICLE_TYPE.get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
            if (p == null) return;
            if (viewer != null) viewer.spawnParticle(p, l, count, spread, spread, spread, speed);
            else l.getWorld().spawnParticle(p, l, count, spread, spread, spread, speed);
        } catch (Exception ignored) {}
    }

    static void sound(Player viewer, Location l, String key, float volume, float pitch) {
        if (key == null || key.isBlank()) return;
        try {
            if (viewer != null) viewer.playSound(l, key, volume, pitch);
            else l.getWorld().playSound(l, key, volume, pitch);
        } catch (Exception ignored) {}
    }
}
