package fr.triplecrates;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MenuListener implements Listener {
    /** Item tenu au curseur au moment du dernier clic dans un menu (lu par l'éditeur : glisser-déposer d'un item). */
    public static final Map<UUID, ItemStack> CURSOR = new HashMap<>();

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        var top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Menu m)) return;

        if (e.getClickedInventory() != top) {
            // Clic dans l'inventaire du joueur : permis (pour prendre un item) sauf ce qui toucherait au menu
            if (m.allowsBottom() && e.getClickedInventory() != null) {
                ClickType t = e.getClick();
                InventoryAction a = e.getAction();
                boolean unsafe = t.isShiftClick() || t == ClickType.DOUBLE_CLICK
                        || a == InventoryAction.MOVE_TO_OTHER_INVENTORY || a == InventoryAction.COLLECT_TO_CURSOR;
                e.setCancelled(unsafe);
            } else e.setCancelled(true);
            return;
        }

        e.setCancelled(true);
        var action = m.action(e.getSlot());
        if (action == null) return;
        ItemStack cur = e.getCursor();
        if (cur != null && !cur.isEmpty()) CURSOR.put(e.getWhoClicked().getUniqueId(), cur.clone());
        else CURSOR.remove(e.getWhoClicked().getUniqueId());
        Bukkit.getScheduler().runTask(TripleCratesPlugin.get(), () -> action.accept(e));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }
}
