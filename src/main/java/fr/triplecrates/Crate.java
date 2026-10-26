package fr.triplecrates;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class Crate {
    public final String id;
    public String name;
    public List<String> description = new ArrayList<>();
    public Material block = Material.CHEST;
    public String keyId;
    public final Hooks.ModelSpec model = new Hooks.ModelSpec();
    public final Map<String, Object> anim = new LinkedHashMap<>(); // surcharges de default-animation
    public String previewTitle, previewFiller;
    public int previewRows;
    public boolean showChance = true, showRarity = true;
    // --- aperçu personnalisable (voir README) ---
    public List<?> previewRewardSlots;              // cases où se placent les récompenses sans preview-slot (null = automatique)
    public String previewFillerArea = "bottom-row"; // bottom-row | all | none
    public YamlConfiguration previewItems;          // boutons / décor (null = ceux de config.yml)
    public List<String> previewRewardLore;          // lore ajouté aux récompenses (null = rareté + chance)
    public final Map<String, Reward> rewards = new LinkedHashMap<>();

    public Crate(String id) {
        this.id = id;
        var cfg = TripleCratesPlugin.get().getConfig();
        name = "<gold>" + id;
        previewTitle = cfg.getString("preview.default-title", "<dark_gray>Récompenses • %crate%");
        previewRows = cfg.getInt("preview.default-rows", 5);
        previewFiller = cfg.getString("preview.default-filler", "BLACK_STAINED_GLASS_PANE");
    }

    public static Crate load(String id, YamlConfiguration y) {
        Crate c = new Crate(id);
        c.name = y.getString("name", c.name);
        c.description = new ArrayList<>(y.getStringList("description"));
        Material m = Material.matchMaterial(y.getString("block", "CHEST"));
        c.block = m != null && m.isBlock() ? m : Material.CHEST;
        c.keyId = y.getString("key");
        c.model.load(y.getConfigurationSection("model"));
        ConfigurationSection a = y.getConfigurationSection("animation");
        if (a != null) for (String k : a.getKeys(false)) c.anim.put(k, a.get(k));
        c.previewTitle = y.getString("preview.title", c.previewTitle);
        c.previewRows = y.getInt("preview.rows", c.previewRows);
        c.previewFiller = y.getString("preview.filler", c.previewFiller);
        c.showChance = y.getBoolean("preview.show-chance", true);
        c.showRarity = y.getBoolean("preview.show-rarity", true);
        if (y.isList("preview.reward-slots")) c.previewRewardSlots = y.getList("preview.reward-slots");
        c.previewFillerArea = y.getString("preview.filler-area", "bottom-row");
        if (y.isList("preview.reward-lore")) c.previewRewardLore = new ArrayList<>(y.getStringList("preview.reward-lore"));
        ConfigurationSection pi = y.getConfigurationSection("preview.items");
        if (pi != null) {
            YamlConfiguration cp = new YamlConfiguration();
            for (String k : pi.getKeys(true)) { Object v = pi.get(k); if (!(v instanceof ConfigurationSection)) cp.set(k, v); }
            c.previewItems = cp;
        }
        ConfigurationSection rs = y.getConfigurationSection("rewards");
        if (rs != null) for (String rid : rs.getKeys(false)) c.rewards.put(rid, Reward.load(rid, rs.getConfigurationSection(rid)));
        return c;
    }

    public YamlConfiguration toYaml() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("name", name);
        y.set("description", description);
        y.set("block", block.name());
        y.set("key", keyId);
        model.save(y.createSection("model"));
        ConfigurationSection a = y.createSection("animation");
        anim.forEach(a::set);
        y.set("preview.title", previewTitle);
        y.set("preview.rows", previewRows);
        y.set("preview.filler", previewFiller);
        y.set("preview.show-chance", showChance);
        y.set("preview.show-rarity", showRarity);
        y.set("preview.filler-area", previewFillerArea);
        if (previewRewardSlots != null) y.set("preview.reward-slots", previewRewardSlots);
        if (previewRewardLore != null) y.set("preview.reward-lore", previewRewardLore);
        if (previewItems != null) {
            y.createSection("preview.items");
            for (String k : previewItems.getKeys(true)) {
                Object v = previewItems.get(k);
                if (!(v instanceof ConfigurationSection)) y.set("preview.items." + k, v);
            }
        }
        ConfigurationSection rs = y.createSection("rewards");
        rewards.forEach((rid, r) -> r.save(rs.createSection(rid)));
        return y;
    }

    public String str(String key) {
        Object o = anim.get(key);
        if (o != null) return o.toString();
        return TripleCratesPlugin.get().getConfig().getString("default-animation." + key, "");
    }

    public double num(String key) {
        try { return Double.parseDouble(str(key)); } catch (NumberFormatException e) { return 0; }
    }

    /** Comme num(), mais avec une valeur par défaut si la clé est absente (anciens fichiers de config). */
    public double num(String key, double def) {
        String s = str(key);
        if (s == null || s.isBlank()) return def;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return def; }
    }

    public double totalWeight() { return rewards.values().stream().mapToDouble(r -> Math.max(0, r.weight)).sum(); }

    public double chance(Reward r) {
        double t = totalWeight();
        return t <= 0 ? 0 : Math.max(0, r.weight) / t * 100.0;
    }

    /** Tire n récompenses distinctes (avec répétition seulement s'il y en a moins que n). */
    public List<Reward> pick(int n) {
        List<Reward> pool = new ArrayList<>(rewards.values()), out = new ArrayList<>();
        while (out.size() < n && !pool.isEmpty()) {
            Reward r = roll(pool);
            pool.remove(r);
            out.add(r);
        }
        while (out.size() < n && !rewards.isEmpty()) out.add(roll(new ArrayList<>(rewards.values())));
        return out;
    }

    private Reward roll(List<Reward> pool) {
        double total = pool.stream().mapToDouble(r -> Math.max(0, r.weight)).sum();
        if (total <= 0) return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        double x = ThreadLocalRandom.current().nextDouble() * total;
        for (Reward r : pool) {
            x -= Math.max(0, r.weight);
            if (x < 0) return r;
        }
        return pool.get(pool.size() - 1);
    }
}
