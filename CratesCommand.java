package fr.triplecrates;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CratesCommand implements TabExecutor {
    private final TripleCratesPlugin pl;

    public CratesCommand(TripleCratesPlugin pl) { this.pl = pl; }

    private boolean admin(CommandSender s) {
        if (s.hasPermission("triplecrates.admin")) return true;
        Text.send(s, "no-permission");
        return false;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 0) { help(s); return true; }
        var cm = pl.crates();
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "editor" -> {
                if (!admin(s)) return true;
                if (s instanceof Player p) pl.editor().main(p); else Text.send(s, "player-only");
            }
            case "reload" -> { if (admin(s)) { pl.reloadAll(); Text.send(s, "reloaded"); } }
            case "list" -> {
                if (!admin(s)) return true;
                s.sendMessage(Text.parse("<gold>Caisses : <white>" + String.join(", ", cm.crates.keySet())));
                s.sendMessage(Text.parse("<gold>Clés : <white>" + String.join(", ", cm.keys.keySet())));
            }
            case "give", "givekey" -> {
                if (!admin(s)) return true;
                if (a.length < 3) { help(s); return true; }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) { Text.send(s, "player-not-found"); return true; }
                int n = 1;
                if (a.length > 3) {
                    try { n = Math.max(1, Integer.parseInt(a[3])); } catch (NumberFormatException e) { Text.send(s, "invalid-number"); return true; }
                }
                boolean key = a[0].equalsIgnoreCase("givekey");
                ItemStack it;
                String shown;
                if (key) {
                    CrateKey k = cm.keys.get(a[2]);
                    if (k == null) { Text.send(s, "key-not-found", "%key%", a[2]); return true; }
                    it = k.build(n);
                    shown = Text.plain(Text.parse(k.name));
                } else {
                    Crate c = cm.crates.get(a[2]);
                    if (c == null) { Text.send(s, "crate-not-found", "%crate%", a[2]); return true; }
                    it = cm.crateItem(c);
                    it.setAmount(n);
                    shown = Text.plain(Text.parse(c.name));
                }
                t.getInventory().addItem(it).values().forEach(o -> t.getWorld().dropItemNaturally(t.getLocation(), o));
                Text.send(s, key ? "key-given" : "crate-given", key ? "%key%" : "%crate%", shown, "%amount%", String.valueOf(n), "%player%", t.getName());
            }
            case "open" -> {
                if (!admin(s)) return true;
                if (a.length < 3) { help(s); return true; }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) { Text.send(s, "player-not-found"); return true; }
                Crate c = cm.crates.get(a[2]);
                if (c == null) { Text.send(s, "crate-not-found", "%crate%", a[2]); return true; }
                if (c.rewards.isEmpty()) { Text.send(s, "empty-crate"); return true; }
                if (OpenSession.ACTIVE.containsKey(t.getUniqueId())) { Text.send(s, "already-opening"); return true; }
                Vector f = t.getLocation().getDirection().setY(0);
                if (f.lengthSquared() < 0.0001) f = new Vector(0, 0, 1);
                var at = t.getLocation().add(f.normalize().multiply(1.5)).add(0, 0.5, 0);
                new OpenSession(pl, t, c, null, at).begin();
            }
            case "preview" -> {
                if (a.length < 2) { help(s); return true; }
                Crate c = cm.crates.get(a[1]);
                if (c == null) { Text.send(s, "crate-not-found", "%crate%", a[1]); return true; }
                Player t = a.length > 2 ? Bukkit.getPlayerExact(a[2]) : (s instanceof Player p ? p : null);
                if (t == null) { Text.send(s, "player-not-found"); return true; }
                if (t != s && !admin(s)) return true;
                PreviewMenu.open(t, c, 0);
            }
            default -> help(s);
        }
        return true;
    }

    private void help(CommandSender s) { for (String l : Text.msgList("help")) s.sendMessage(Text.parse(l)); }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (!s.hasPermission("triplecrates.admin")) return out;
        var cm = pl.crates();
        if (a.length == 1) out.addAll(List.of("editor", "give", "givekey", "open", "preview", "list", "reload"));
        else if (a.length == 2 && List.of("give", "givekey", "open").contains(a[0].toLowerCase())) Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        else if (a.length == 2 && a[0].equalsIgnoreCase("preview")) out.addAll(cm.crates.keySet());
        else if (a.length == 3 && List.of("give", "open").contains(a[0].toLowerCase())) out.addAll(cm.crates.keySet());
        else if (a.length == 3 && a[0].equalsIgnoreCase("givekey")) out.addAll(cm.keys.keySet());
        String last = a[a.length - 1].toLowerCase();
        out.removeIf(x -> !x.toLowerCase().startsWith(last));
        return out;
    }
}
