package fr.triplecrates;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class Menu implements InventoryHolder {
    private final Inventory inv;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();
    private boolean allowBottom;

    public Menu(Component title, int rows) { inv = Bukkit.createInventory(this, rows * 9, title); }

    public Menu set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        inv.setItem(slot, item);
        if (action != null) actions.put(slot, action); else actions.remove(slot);
        return this;
    }

    public Menu fill(ItemStack it) {
        for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null) inv.setItem(i, it);
        return this;
    }

    public Menu fillRange(int from, int toExcl, ItemStack it) {
        for (int i = from; i < toExcl; i++) inv.setItem(i, it);
        return this;
    }

    /** Autorise le joueur à prendre un item dans son propre inventaire (pour le déposer sur un slot du menu). */
    public Menu allowBottom(boolean b) { this.allowBottom = b; return this; }
    public boolean allowsBottom() { return allowBottom; }

    public Consumer<InventoryClickEvent> action(int slot) { return actions.get(slot); }
    public void open(Player p) { p.openInventory(inv); }
    @Override public Inventory getInventory() { return inv; }
}
