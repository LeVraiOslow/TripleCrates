package fr.triplecrates;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class CrateKey {
    public final String id;
    public String item = "TRIPWIRE_HOOK";
    public String name;
    public List<String> lore = new ArrayList<>();
    public String rarity = "<gray>Commune";
    public boolean glow = true;

    public CrateKey(String id) { this.id = id; this.name = "<yellow>Clé " + id; }

    public static CrateKey load(String id, ConfigurationSection s) {
        CrateKey k = new CrateKey(id);
        k.item = s.getString("item", "TRIPWIRE_HOOK");
        k.name = s.getString("name", k.name);
        k.lore = new ArrayList<>(s.getStringList("lore"));
        k.rarity = s.getString("rarity", k.rarity);
        k.glow = s.getBoolean("glow", true);
        return k;
    }

    public void save(ConfigurationSection s) {
        s.set("item", item);
        s.set("name", name);
        s.set("lore", lore);
        s.set("rarity", rarity);
        s.set("glow", glow);
    }

    public ItemStack build(int amount) {
        ItemStack it = ItemUtil.parse(item, amount);
        ItemMeta m = it.getItemMeta();
        m.displayName(Text.item(name));
        m.lore(lore.stream().map(l -> Text.item(l.replace("%rarity%", rarity))).toList());
        if (glow) m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(TripleCratesPlugin.get().K_KEY, PersistentDataType.STRING, id);
        it.setItemMeta(m);
        return it;
    }

    public static String idOf(ItemStack it) {
        if (it == null || it.isEmpty() || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(TripleCratesPlugin.get().K_KEY, PersistentDataType.STRING);
    }
}
