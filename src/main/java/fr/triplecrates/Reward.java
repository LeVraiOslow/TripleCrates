package fr.triplecrates;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class Reward {
    public enum Type { ITEM, COMMAND }

    public final String id;
    public String item = "STONE";       // MATERIAL | nexo:<id> | base64:...   (icône si type COMMAND)
    public int amount = 1;
    public String name;                 // surcharge d'affichage (MiniMessage), null = nom de l'item
    public List<String> lore = new ArrayList<>();
    public String rarity = "common";
    public double weight = 10;
    public Type type = Type.ITEM;
    public List<String> commands = new ArrayList<>();
    public boolean broadcast;
    public Integer previewSlot;         // case dans l'aperçu (négatif = depuis la fin), null = placement automatique

    public Reward(String id) { this.id = id; }

    public static Reward load(String id, ConfigurationSection s) {
        Reward r = new Reward(id);
        r.item = s.getString("item", "STONE");
        r.amount = Math.max(1, s.getInt("amount", 1));
        r.name = s.getString("name");
        r.lore = new ArrayList<>(s.getStringList("lore"));
        r.rarity = s.getString("rarity", "common");
        r.weight = s.getDouble("weight", 10);
        r.type = "COMMAND".equalsIgnoreCase(s.getString("type")) ? Type.COMMAND : Type.ITEM;
        r.commands = new ArrayList<>(s.getStringList("commands"));
        r.broadcast = s.getBoolean("broadcast", false);
        r.previewSlot = s.contains("preview-slot") ? s.getInt("preview-slot") : null;
        return r;
    }

    public void save(ConfigurationSection s) {
        s.set("type", type.name());
        s.set("item", item);
        s.set("amount", amount);
        s.set("name", name);
        s.set("lore", lore);
        s.set("rarity", rarity);
        s.set("weight", weight);
        s.set("commands", commands);
        s.set("broadcast", broadcast);
        s.set("preview-slot", previewSlot);
    }

    /** Item affiché (hologramme, aperçu, éditeur). */
    public ItemStack displayItem() {
        ItemStack it = ItemUtil.parse(item, 1);
        it.setAmount(Math.max(1, Math.min(amount, it.getMaxStackSize())));
        if (name != null || !lore.isEmpty()) {
            ItemMeta m = it.getItemMeta();
            if (name != null) m.displayName(Text.item(name));
            if (!lore.isEmpty()) {
                List<Component> l = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
                for (String s : lore) l.add(Text.item(s));
                m.lore(l);
            }
            it.setItemMeta(m);
        }
        return it;
    }

    public Component nameComponent() {
        if (name != null) return Text.item(name);
        Component base = ItemUtil.parse(item, 1).effectiveName();
        return amount > 1 && type == Type.ITEM ? base.append(Component.text(" x" + amount)) : base;
    }

    public void give(Player p, Crate crate) {
        if (type == Type.ITEM) {
            ItemStack it = ItemUtil.parse(item, 1);
            int left = amount, max = Math.max(1, it.getMaxStackSize());
            while (left > 0) {
                ItemStack part = it.clone();
                part.setAmount(Math.min(max, left));
                left -= part.getAmount();
                p.getInventory().addItem(part).values().forEach(o -> p.getWorld().dropItemNaturally(p.getLocation(), o));
            }
        } else {
            for (String raw : commands) {
                String cmd = raw.replace("%player%", p.getName()).replace("%uuid%", p.getUniqueId().toString())
                        .replace("%crate%", Text.plain(Text.parse(crate.name))).replace("%reward%", Text.plain(nameComponent()));
                if (cmd.regionMatches(true, 0, "[player]", 0, 8)) p.performCommand(cmd.substring(8).trim());
                else Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.regionMatches(true, 0, "[console]", 0, 9) ? cmd.substring(9).trim() : cmd);
            }
        }
        if (broadcast) {
            Bukkit.broadcast(Text.parse(Text.msgRaw("broadcast", "%player%", p.getName(),
                    "%reward%", Text.plain(nameComponent()), "%rarity%", TripleCratesPlugin.get().crates().rarityName(rarity),
                    "%crate%", Text.plain(Text.parse(crate.name)))));
        }
    }
}
