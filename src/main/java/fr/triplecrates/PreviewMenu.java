package fr.triplecrates;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Aperçu des récompenses, entièrement configurable par caisse (section "preview" du fichier de la caisse) :
 * lignes, titre, placement des récompenses (preview-slot / reward-slots), boutons et décor (items, avec icônes Nexo),
 * zone de remplissage. Sans configuration, on retrouve la mise en page par défaut de config.yml.
 */
public final class PreviewMenu {
    private PreviewMenu() {}

    private record Btn(List<Integer> slots, ItemStack item, String action, List<String> commands, String show) {}

    public static void open(Player p, Crate c, int page) {
        var pl = TripleCratesPlugin.get();
        var cfg = pl.getConfig();
        int rows = Math.max(1, Math.min(6, c.previewRows));
        int size = rows * 9;
        String title = c.previewTitle.replace("%crate%", Text.plain(Text.parse(c.name)));
        Menu m = new Menu(Text.parse(title), rows);

        // 1) boutons et décor : leurs cases ne reçoivent jamais de récompense
        List<Btn> btns = buttons(c, cfg, size);
        Set<Integer> occupied = new HashSet<>();
        for (Btn b : btns) occupied.addAll(b.slots());

        // 2) récompenses à case fixe (preview-slot), les autres seront placées automatiquement
        Map<Integer, Reward> fixed = new LinkedHashMap<>();
        List<Reward> auto = new ArrayList<>();
        for (Reward r : c.rewards.values()) {
            int sl = r.previewSlot == null ? -1 : norm(r.previewSlot, size);
            if (r.previewSlot != null && sl >= 0 && sl < size && !occupied.contains(sl) && !fixed.containsKey(sl)) fixed.put(sl, r);
            else auto.add(r);
        }

        // 3) cases disponibles pour le placement automatique (reward-slots, ou tout sauf la dernière ligne)
        List<Integer> area = new ArrayList<>();
        if (c.previewRewardSlots != null) addSlots(c.previewRewardSlots, size, area);
        else for (int i = 0; i < (rows > 1 ? size - 9 : size); i++) area.add(i);
        List<Integer> free = new ArrayList<>();
        for (int s : new LinkedHashSet<>(area)) if (s >= 0 && s < size && !occupied.contains(s) && !fixed.containsKey(s)) free.add(s);

        int per = free.size();
        int pages = per == 0 ? 1 : Math.max(1, (auto.size() + per - 1) / per);
        int pg = Math.max(0, Math.min(page, pages - 1));

        fixed.forEach((slot, r) -> m.set(slot, rewardIcon(c, r), null));
        for (int i = 0; i < per; i++) {
            int idx = pg * per + i;
            if (idx >= auto.size()) break;
            m.set(free.get(i), rewardIcon(c, auto.get(idx)), null);
        }

        // 4) boutons / décor (par-dessus, avec leurs actions)
        for (Btn b : btns) {
            boolean show = switch (b.show()) {
                case "has-next" -> pg < pages - 1;
                case "has-prev" -> pg > 0;
                default -> true;
            };
            if (!show) continue;
            for (int slot : b.slots()) m.set(slot, b.item().clone(), b.action().equals("none") ? null : e -> run(p, c, b, pg, pages));
        }

        // 5) remplissage des cases vides
        ItemStack filler = filler(c);
        String area2 = c.previewFillerArea == null ? "bottom-row" : c.previewFillerArea.toLowerCase(Locale.ROOT);
        if (filler != null && !area2.equals("none")) {
            int from = area2.equals("all") || rows == 1 ? 0 : size - 9;
            for (int i = from; i < size; i++) if (m.getInventory().getItem(i) == null) m.set(i, filler.clone(), null);
        }
        m.open(p);
    }

    // ---------------------------------------------------------------------------------------------------

    private static ItemStack rewardIcon(Crate c, Reward r) {
        var pl = TripleCratesPlugin.get();
        var cfg = pl.getConfig();
        ItemStack it = r.displayItem();
        ItemMeta meta = it.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        TagResolver tr = TagResolver.resolver(
                Placeholder.component("name", r.nameComponent()),
                Placeholder.parsed("rarity", pl.crates().rarityName(r.rarity)),
                Placeholder.parsed("chance", Text.num(c.chance(r))),
                Placeholder.parsed("weight", Text.num(r.weight)),
                Placeholder.parsed("amount", String.valueOf(r.amount)));
        if (c.previewRewardLore != null) {
            for (String l : c.previewRewardLore) lore.add(Text.parseWith(l, tr));
        } else {
            if (c.showRarity || c.showChance) lore.add(Component.empty());
            if (c.showRarity) lore.add(Text.parseWith(cfg.getString("preview.lore.rarity", "<gray>Rareté : <rarity>"), tr));
            if (c.showChance) lore.add(Text.parseWith(cfg.getString("preview.lore.chance", "<gray>Chance : <yellow><chance>%"), tr));
        }
        meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack filler(Crate c) {
        String spec = c.previewFiller;
        if (spec == null || spec.isBlank() || spec.equalsIgnoreCase("none") || spec.equalsIgnoreCase("air")) return null;
        ItemStack f = ItemUtil.parse(spec, 1);
        ItemMeta fm = f.getItemMeta();
        if (fm != null && !fm.hasDisplayName()) { fm.displayName(Component.text(" ")); f.setItemMeta(fm); }
        return f;
    }

    /** Boutons et décor : section "preview.items" de la caisse si elle existe (remplace tout), sinon celle de config.yml. */
    private static List<Btn> buttons(Crate c, ConfigurationSection cfg, int size) {
        ConfigurationSection src = c.previewItems != null ? c.previewItems : cfg.getConfigurationSection("preview.items");
        List<Btn> out = new ArrayList<>();
        if (src == null) return out;
        for (String key : src.getKeys(false)) {
            ConfigurationSection b = src.getConfigurationSection(key);
            if (b == null) continue;
            String lk = key.toLowerCase(Locale.ROOT);
            String defSlot = switch (lk) { case "prev" -> "-9"; case "next" -> "-1"; case "close" -> "-5"; default -> null; };
            List<Integer> slots = new ArrayList<>();
            if (b.contains("slots")) addSlots(b.get("slots"), size, slots);
            else if (b.contains("slot")) addSlots(b.get("slot"), size, slots);
            else if (defSlot != null) addSlots(defSlot, size, slots);
            slots.removeIf(s -> s < 0 || s >= size);
            if (slots.isEmpty()) continue;

            String def = switch (lk) { case "prev" -> "prev-page"; case "next" -> "next-page"; case "close" -> "close"; default -> "none"; };
            String action = switch (b.getString("action", def).toLowerCase(Locale.ROOT)) {
                case "close" -> "close";
                case "next", "next-page" -> "next-page";
                case "prev", "previous", "prev-page", "previous-page" -> "prev-page";
                case "command", "commands" -> "command";
                default -> "none";
            };
            String show = b.getString("show", switch (action) { case "next-page" -> "has-next"; case "prev-page" -> "has-prev"; default -> "always"; })
                    .toLowerCase(Locale.ROOT);
            out.add(new Btn(slots, ItemUtil.fromSection(b, "STONE"), action, b.getStringList("commands"), show));
        }
        return out;
    }

    private static void run(Player p, Crate c, Btn b, int pg, int pages) {
        switch (b.action()) {
            case "close" -> p.closeInventory();
            case "next-page" -> { if (pg < pages - 1) open(p, c, pg + 1); }
            case "prev-page" -> { if (pg > 0) open(p, c, pg - 1); }
            case "command" -> {
                for (String raw : b.commands()) {
                    String cmd = raw.replace("%player%", p.getName()).replace("%crate%", Text.plain(Text.parse(c.name))).trim();
                    boolean close = false;
                    if (cmd.regionMatches(true, 0, "[close]", 0, 7)) { close = true; cmd = cmd.substring(7).trim(); }
                    if (cmd.regionMatches(true, 0, "[console]", 0, 9)) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.substring(9).trim());
                    else if (!cmd.isEmpty()) p.performCommand(cmd.regionMatches(true, 0, "[player]", 0, 8) ? cmd.substring(8).trim() : cmd);
                    if (close) p.closeInventory();
                }
            }
            default -> {}
        }
    }

    // ---------- cases : 13, "10-16", "-1" (depuis la fin), ou liste de tout ça ----------
    private static int norm(int s, int size) { return s < 0 ? size + s : s; }

    private static void addSlots(Object o, int size, List<Integer> out) {
        if (o instanceof Collection<?> l) { for (Object x : l) addSlots(x, size, out); return; }
        if (o instanceof Number n) { out.add(norm(n.intValue(), size)); return; }
        if (o instanceof String str) {
            String s = str.trim();
            try {
                int dash = s.indexOf('-', 1);
                if (dash > 0) {
                    int a = norm(Integer.parseInt(s.substring(0, dash).trim()), size), z = norm(Integer.parseInt(s.substring(dash + 1).trim()), size);
                    for (int i = Math.min(a, z); i <= Math.max(a, z); i++) out.add(i);
                } else if (!s.isEmpty()) out.add(norm(Integer.parseInt(s), size));
            } catch (NumberFormatException ignored) {}
        }
    }
}
