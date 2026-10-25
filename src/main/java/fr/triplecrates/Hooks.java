package fr.triplecrates;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Intégrations par réflexion : Nexo, MythicMobs, ModelEngine (aucune dépendance de compilation). */
public final class Hooks {
    public static boolean nexo, mythic, modelEngine;

    private Hooks() {}

    public static void init() {
        var pm = Bukkit.getPluginManager();
        nexo = pm.isPluginEnabled("Nexo");
        mythic = pm.isPluginEnabled("MythicMobs");
        modelEngine = pm.isPluginEnabled("ModelEngine");
    }

    /** Description d'un modèle 3D (caisse placée ou coffre d'animation). */
    public static final class ModelSpec {
        public String type = "NONE"; // NONE | MYTHICMOBS | MODELENGINE
        public String id = "";
        public double yOffset = 0, width = 1.0, height = 1.2;

        public boolean active() { return !"NONE".equalsIgnoreCase(type) && id != null && !id.isBlank(); }

        public void load(ConfigurationSection s) {
            if (s == null) return;
            type = s.getString("type", "NONE").toUpperCase();
            id = s.getString("id", "");
            yOffset = s.getDouble("y-offset", 0);
            width = s.getDouble("hitbox-width", 1.0);
            height = s.getDouble("hitbox-height", 1.2);
        }

        public void save(ConfigurationSection s) {
            s.set("type", type);
            s.set("id", id);
            s.set("y-offset", yOffset);
            s.set("hitbox-width", width);
            s.set("hitbox-height", height);
        }
    }

    // ---------- Nexo ----------
    public static ItemStack nexoItem(String id) {
        if (!nexo) return null;
        try {
            Object builder = callStatic(Class.forName("com.nexomc.nexo.api.NexoItems"), "itemFromId", id);
            if (builder == null) return null;
            return (ItemStack) call(builder, "build");
        } catch (Throwable t) {
            return null;
        }
    }

    public static String nexoId(ItemStack it) {
        if (!nexo || it == null || it.isEmpty()) return null;
        try {
            return (String) callStatic(Class.forName("com.nexomc.nexo.api.NexoItems"), "idFromItem", it);
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------- MythicMobs / ModelEngine ----------
    public static Entity spawnMythic(String type, Location loc) {
        if (!mythic) return null;
        try {
            Object inst = callStatic(Class.forName("io.lumine.mythic.bukkit.MythicBukkit"), "inst");
            Object mobs = call(inst, "getMobManager");
            Object active = call(mobs, "spawnMob", type, loc);
            if (active == null) return null;
            return (Entity) call(call(active, "getEntity"), "getBukkitEntity");
        } catch (Throwable t) {
            TripleCratesPlugin.get().getLogger().warning("MythicMobs: impossible de faire apparaître '" + type + "' : " + t);
            return null;
        }
    }

    public static void attachModelEngine(Entity base, String blueprint) {
        if (!modelEngine) return;
        try {
            Class<?> api = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
            Object active = callStatic(api, "createActiveModel", blueprint);
            Object modeled = callStatic(api, "createModeledEntity", base);
            call(modeled, "addModel", active, true);
            try { call(modeled, "setBaseEntityVisible", false); } catch (Throwable ignored) {}
        } catch (Throwable t) {
            TripleCratesPlugin.get().getLogger().warning("ModelEngine: impossible d'appliquer '" + blueprint + "' : " + t);
        }
    }

    /**
     * Les modèles ModelEngine sont envoyés par ModelEngine lui-même (paquets), donc Player#hideEntity ne les
     * concerne pas : on passe par son API (BaseEntity#setForceHidden). Sans effet si ModelEngine est absent
     * ou si l'entité n'a pas de modèle.
     */
    public static void setModelVisible(Entity base, Player viewer, boolean visible) {
        if (!modelEngine || base == null || viewer == null) return;
        try {
            Object modeled = callStatic(Class.forName("com.ticxo.modelengine.api.ModelEngineAPI"), "getModeledEntity", base);
            if (modeled instanceof java.util.Optional<?> o) modeled = o.orElse(null);
            if (modeled == null) return;
            Object b = call(modeled, "getBase");
            if (b != null) call(b, "setForceHidden", viewer, !visible);
        } catch (Throwable ignored) {}
    }

    // ---------- réflexion ----------
    private static Object call(Object target, String name, Object... args) throws Exception {
        return invoke(target.getClass(), target, name, args);
    }

    private static Object callStatic(Class<?> c, String name, Object... args) throws Exception {
        return invoke(c, null, name, args);
    }

    private static Object invoke(Class<?> c, Object target, String name, Object[] args) throws Exception {
        for (Method m : c.getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
            Class<?>[] pt = m.getParameterTypes();
            boolean ok = true;
            for (int i = 0; i < pt.length; i++) {
                if (args[i] == null || !wrap(pt[i]).isInstance(args[i])) { ok = false; break; }
            }
            if (!ok) continue;
            try { m.setAccessible(true); } catch (Exception ignored) {}
            Object t = null;
            if (!Modifier.isStatic(m.getModifiers())) t = target != null ? target : c.getField("INSTANCE").get(null);
            return m.invoke(t, args);
        }
        throw new NoSuchMethodException(c.getName() + "#" + name);
    }

    private static Class<?> wrap(Class<?> c) {
        if (!c.isPrimitive()) return c;
        if (c == boolean.class) return Boolean.class;
        if (c == int.class) return Integer.class;
        if (c == double.class) return Double.class;
        if (c == float.class) return Float.class;
        if (c == long.class) return Long.class;
        return c;
    }
}
