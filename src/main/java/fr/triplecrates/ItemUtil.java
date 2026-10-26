package fr.triplecrates;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/** Formats d'item : MATERIAL, nexo:<id>, base64:<ItemStack sérialisé> (outils d'autres plugins, NBT conservé). */
public final class ItemUtil {
    private ItemUtil() {}

    public static ItemStack resolve(String spec) {
        if (spec == null || spec.isBlank()) return null;
        String s = spec.trim();
        try {
            if (s.regionMatches(true, 0, "nexo:", 0, 5)) return Hooks.nexoItem(s.substring(5));
            if (s.regionMatches(true, 0, "base64:", 0, 7)) return ItemStack.deserializeBytes(Base64.getDecoder().decode(s.substring(7)));
            Material m = Material.matchMaterial(s);
            if (m != null && m.isItem() && !m.isAir()) return new ItemStack(m);
        } catch (Exception ignored) {}
        return null;
    }

    public static ItemStack parse(String spec, int amount) {
        ItemStack it = resolve(spec);
        if (it == null) it = icon(Material.BARRIER, "<red>Item invalide", "<gray>" + spec);
        it = it.clone();
        it.setAmount(Math.max(1, amount));
        return it;
    }

    /** Encode un item tenu en main : nexo:<id> > matériau simple > base64 (conserve tout). */
    public static String encode(ItemStack it) {
        String nid = Hooks.nexoId(it);
        if (nid != null) return "nexo:" + nid;
        ItemStack one = it.asOne();
        if (one.isSimilar(new ItemStack(one.getType()))) return one.getType().name();
        return "base64:" + Base64.getEncoder().encodeToString(one.serializeAsBytes());
    }

    public static ItemStack icon(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m.isItem() && !m.isAir() ? m : Material.STONE);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Text.item(name));
        if (lore.length > 0) meta.lore(Arrays.stream(lore).map(Text::item).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(meta);
        return it;
    }

    public static ItemStack withLore(ItemStack it, String... extra) {
        ItemStack c = it.clone();
        ItemMeta meta = c.getItemMeta();
        List<Component> l = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        for (String s : extra) l.add(Text.item(s));
        meta.lore(l);
        c.setItemMeta(meta);
        return c;
    }

    /** item / name / lore / amount / glow / hide-tooltip depuis une section de config (item : matériau, nexo:<id>, base64:...). */
    public static ItemStack fromSection(ConfigurationSection s, String defItem) {
        ItemStack it = parse(s == null ? defItem : s.getString("item", defItem), 1);
        if (s == null) return it;
        if (s.contains("amount")) it.setAmount(Math.max(1, Math.min(it.getMaxStackSize(), s.getInt("amount", 1))));
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        if (s.contains("name")) meta.displayName(Text.item(s.getString("name")));
        List<String> lore = s.getStringList("lore");
        if (!lore.isEmpty()) meta.lore(lore.stream().map(Text::item).toList());
        if (s.getBoolean("glow", false)) meta.setEnchantmentGlintOverride(true);
        if (s.getBoolean("hide-tooltip", false)) meta.setHideTooltip(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(meta);
        return it;
    }
}
