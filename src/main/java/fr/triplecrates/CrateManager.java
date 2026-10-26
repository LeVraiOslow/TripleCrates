package fr.triplecrates;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class CrateManager {
    private final TripleCratesPlugin pl;
    public final Map<String, String> rarities = new LinkedHashMap<>();
    public final Map<String, CrateKey> keys = new LinkedHashMap<>();
    public final Map<String, Crate> crates = new LinkedHashMap<>();
    public final Map<String, String> placed = new LinkedHashMap<>(); // "monde;x;y;z" -> id de caisse
    private final Map<String, ModelVisual> visuals = new HashMap<>();

    public CrateManager(TripleCratesPlugin pl) { this.pl = pl; }

    private File file(String n) { return new File(pl.getDataFolder(), n); }

    private void write(YamlConfiguration y, File f) {
        try { y.save(f); } catch (IOException e) { pl.getLogger().severe("Sauvegarde impossible " + f.getName() + " : " + e); }
    }

    // ---------- chargement / sauvegarde ----------
    public void loadAll() {
        removeAllVisuals();
        rarities.clear(); keys.clear(); crates.clear(); placed.clear();

        ConfigurationSection rs = YamlConfiguration.loadConfiguration(file("rarities.yml")).getConfigurationSection("rarities");
        if (rs != null) for (String k : rs.getKeys(false)) rarities.put(k, rs.getString(k, k));

        ConfigurationSection ks = YamlConfiguration.loadConfiguration(file("keys.yml")).getConfigurationSection("keys");
        if (ks != null) for (String k : ks.getKeys(false)) keys.put(k, CrateKey.load(k, ks.getConfigurationSection(k)));

        File[] files = file("crates").listFiles((d, n) -> n.endsWith(".yml"));
        if (files != null) for (File f : files) {
            String id = f.getName().substring(0, f.getName().length() - 4);
            crates.put(id, Crate.load(id, YamlConfiguration.loadConfiguration(f)));
        }

        for (String line : YamlConfiguration.loadConfiguration(file("data.yml")).getStringList("placed")) {
            int i = line.lastIndexOf('|');
            if (i > 0) placed.put(line.substring(0, i), line.substring(i + 1));
        }
    }

    public void saveRarities() {
        YamlConfiguration y = new YamlConfiguration();
        rarities.forEach((k, v) -> y.set("rarities." + k, v));
        write(y, file("rarities.yml"));
    }

    public void saveKeys() {
        YamlConfiguration y = new YamlConfiguration();
        keys.forEach((k, v) -> v.save(y.createSection("keys." + k)));
        write(y, file("keys.yml"));
    }

    public void saveCrate(Crate c) {
        crates.put(c.id, c);
        File dir = file("crates");
        dir.mkdirs();
        write(c.toYaml(), new File(dir, c.id + ".yml"));
    }

    public void deleteCrate(Crate c) {
        crates.remove(c.id);
        new File(file("crates"), c.id + ".yml").delete();
        for (String k : new ArrayList<>(placed.keySet())) if (placed.get(k).equals(c.id)) { removeVisual(k); placed.remove(k); }
        savePlaced();
    }

    public void savePlaced() {
        YamlConfiguration y = new YamlConfiguration();
        List<String> lines = new ArrayList<>();
        placed.forEach((k, v) -> lines.add(k + "|" + v));
        y.set("placed", lines);
        write(y, file("data.yml"));
    }

    public String rarityName(String id) { return Text.preprocess(rarities.getOrDefault(id, id)); }

    public static String sanitize(String s) {
        String r = s.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
        return r.matches("[a-z0-9_-]{1,32}") ? r : null;
    }

    // ---------- caisses posées ----------
    public static String locKey(Block b) { return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ(); }

    public static Location parseLoc(String key) {
        String[] p = key.split(";");
        if (p.length != 4) return null;
        World w = Bukkit.getWorld(p[0]);
        if (w == null) return null;
        try { return new Location(w, Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])); }
        catch (NumberFormatException e) { return null; }
    }

    public Crate crateAt(Block b) { return crateAtKey(locKey(b)); }

    public Crate crateAtKey(String key) {
        String id = placed.get(key);
        return id == null ? null : crates.get(id);
    }

    public void register(Block b, Crate c) {
        String k = locKey(b);
        placed.put(k, c.id);
        savePlaced();
        ensureVisual(k);
    }

    public void unregister(Block b) {
        String k = locKey(b);
        removeVisual(k);
        placed.remove(k);
        savePlaced();
    }

    public ItemStack crateItem(Crate c) {
        ItemStack it = new ItemStack(c.block.isItem() ? c.block : Material.CHEST);
        ItemMeta m = it.getItemMeta();
        m.displayName(Text.item(c.name));
        m.lore(c.description.stream().map(Text::item).toList());
        m.getPersistentDataContainer().set(pl.K_CRATE_ITEM, PersistentDataType.STRING, c.id);
        it.setItemMeta(m);
        return it;
    }

    // ---------- modèles 3D des caisses posées ----------
    public void ensureAllVisuals() { for (String k : new ArrayList<>(placed.keySet())) ensureVisual(k); }

    public void ensureVisual(String key) {
        Crate c = crateAtKey(key);
        if (c == null) return;
        Location bl = parseLoc(key);
        if (bl == null || !bl.getWorld().isChunkLoaded(bl.getBlockX() >> 4, bl.getBlockZ() >> 4)) return;
        if (!c.model.active()) { restoreBlock(bl, c); return; }
        ModelVisual v = visuals.get(key);
        if (v != null && v.valid() && v.hasModel()) { hideVanillaBlock(bl); return; }
        if (v != null) { v.remove(); visuals.remove(key); }   // modèle non appliqué (ex: ModelEngine pas encore prêt) : on réessaie
        Location l = bl.clone().add(0.5, 0, 0.5);
        v = ModelVisual.spawn(l, c.model, null, pl.K_LOC, key, null);
        visuals.put(key, v);
        if (v.hasModel()) hideVanillaBlock(bl);
    }

    /**
     * Avec un modèle 3D, le bloc vanilla (coffre...) ne doit plus se voir : on le remplace par une barrière
     * (invisible, mais toujours solide et cliquable). Seulement une fois le modèle bien appliqué.
     */
    private void hideVanillaBlock(Location bl) {
        Block b = bl.getBlock();
        if (b.getType() != Material.BARRIER && !b.getType().isAir()) b.setType(Material.BARRIER, false);
    }

    /** Plus de modèle sur cette caisse : on remet son bloc d'origine à la place de la barrière. */
    private void restoreBlock(Location bl, Crate c) {
        Block b = bl.getBlock();
        if (b.getType() == Material.BARRIER && c.block != Material.BARRIER && c.block.isBlock()) b.setType(c.block, false);
    }

    public ModelVisual visual(String key) { return visuals.get(key); }

    public void removeVisual(String key) {
        ModelVisual v = visuals.remove(key);
        if (v != null) v.remove();
    }

    public void removeAllVisuals() {
        visuals.values().forEach(ModelVisual::remove);
        visuals.clear();
    }
}
