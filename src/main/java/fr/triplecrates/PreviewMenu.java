package fr.triplecrates;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class PreviewMenu {
    private PreviewMenu() {}

    public static void open(Player p, Crate c, int page) {
        var pl = TripleCratesPlugin.get();
        var cfg = pl.getConfig();
        int rows = Math.max(3, Math.min(6, c.previewRows));
        String title = c.previewTitle.replace("%crate%", Text.plain(Text.parse(c.name)));
        Menu m = new Menu(Text.parse(title), rows);

        int per = (rows - 1) * 9;
        List<Reward> list = new ArrayList<>(c.rewards.values());
        int pages = Math.max(1, (list.size() + per - 1) / per);
        int pg = Math.max(0, Math.min(page, pages - 1));

        for (int i = 0; i < per; i++) {
            int idx = pg * per + i;
            if (idx >= list.size()) break;
            Reward r = list.get(idx);
            ItemStack it = r.displayItem();
            ItemMeta meta = it.getItemMeta();
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            TagResolver tr = TagResolver.resolver(
                    Placeholder.parsed("rarity", pl.crates().rarityName(r.rarity)),
                    Placeholder.parsed("chance", Text.num(c.chance(r))));
            if (c.showRarity || c.showChance) lore.add(Component.empty());
            if (c.showRarity) lore.add(Text.parseWith(cfg.getString("preview.lore.rarity", "<gray>Rareté : <rarity>"), tr));
            if (c.showChance) lore.add(Text.parseWith(cfg.getString("preview.lore.chance", "<gray>Chance : <yellow><chance>%"), tr));
            meta.lore(lore);
            it.setItemMeta(meta);
            m.set(i, it, null);
        }

        ItemStack filler = ItemUtil.parse(c.previewFiller, 1);
        ItemMeta fm = filler.getItemMeta();
        if (fm != null && !fm.hasDisplayName()) { fm.displayName(Component.text(" ")); filler.setItemMeta(fm); }
        m.fillRange(per, rows * 9, filler);

        if (pg > 0) m.set(per, nav("prev"), e -> open(p, c, pg - 1));
        if (pg < pages - 1) m.set(per + 8, nav("next"), e -> open(p, c, pg + 1));
        m.set(per + 4, nav("close"), e -> p.closeInventory());
        m.open(p);
    }

    private static ItemStack nav(String key) {
        var s = TripleCratesPlugin.get().getConfig().getConfigurationSection("preview.items." + key);
        return ItemUtil.fromSection(s, "ARROW");
    }
}
