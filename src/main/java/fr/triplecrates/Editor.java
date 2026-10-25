package fr.triplecrates;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

/** Éditeur en jeu : /crates editor */
public final class Editor {
    private final TripleCratesPlugin pl;

    private static final List<String> PARTICLES = List.of("explosion_emitter", "explosion", "flame", "soul_fire_flame", "cloud",
            "poof", "firework", "totem_of_undying", "end_rod", "happy_villager", "smoke", "lava", "heart", "note", "witch", "portal", "enchant");
    private static final List<String> SOUNDS = List.of("entity.generic.explode", "entity.firework_rocket.blast", "entity.firework_rocket.twinkle",
            "block.anvil.land", "block.chest.open", "block.chest.close", "entity.player.levelup", "entity.experience_orb.pickup",
            "ui.toast.challenge_complete", "block.beacon.activate", "entity.lightning_bolt.thunder", "block.amethyst_block.chime");
    private static final List<String> MODEL_TYPES = List.of("NONE", "MYTHICMOBS", "MODELENGINE");

    private record AS(String key, String label, Material icon, String kind, double step, double min, double max) {}
    private static final List<AS> ANIM = List.of(
            new AS("explosion-particle", "Particule d'explosion", Material.TNT, "P", 0, 0, 0),
            new AS("explosion-sound", "Son d'explosion", Material.NOTE_BLOCK, "S", 0, 0, 0),
            new AS("trail-particle", "Particule de chute", Material.FEATHER, "P", 0, 0, 0),
            new AS("land-particle", "Particule d'impact", Material.ANVIL, "P", 0, 0, 0),
            new AS("land-sound", "Son d'impact", Material.BELL, "S", 0, 0, 0),
            new AS("win-particle", "Particule de victoire", Material.FIREWORK_STAR, "P", 0, 0, 0),
            new AS("win-sound", "Son de victoire", Material.GOAT_HORN, "S", 0, 0, 0),
            new AS("start-delay", "Délai avant la chute (ticks)", Material.CLOCK, "N", 2, 0, 100),
            new AS("fall-height", "Hauteur de chute (blocs)", Material.LADDER, "N", 2, 4, 60),
            new AS("fall-ticks", "Durée de la chute (ticks)", Material.HOPPER, "N", 2, 6, 100),
            new AS("distance", "Distance devant le joueur", Material.ARROW, "N", 0.5, 1, 10),
            new AS("spacing", "Écart entre les coffres", Material.LEAD, "N", 0.5, 1, 6),
            new AS("choose-timeout", "Temps pour choisir (s)", Material.COMPASS, "N", 5, 5, 300),
            new AS("end-delay", "Délai de fin (ticks)", Material.REPEATER, "N", 10, 0, 200),
            new AS("chest-item", "Item / modèle Nexo des coffres", Material.CHEST, "I", 0, 0, 0),
            new AS("chest-model-type", "Modèle des coffres : type", Material.ARMOR_STAND, "M", 0, 0, 0),
            new AS("chest-model-id", "Modèle des coffres : ID", Material.NAME_TAG, "T", 0, 0, 0));
    private static final int[] ANIM_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30};

    public Editor(TripleCratesPlugin pl) { this.pl = pl; }

    private CrateManager cm() { return pl.crates(); }
    private ItemStack glass() { return ItemUtil.icon(Material.GRAY_STAINED_GLASS_PANE, " "); }
    private ItemStack ic(Material m, String name, String... lore) { return ItemUtil.icon(m, name, lore); }
    private Menu menu(String key, String def, int rows, String id) {
        return new Menu(Text.parse(Text.gui(key, def).replace("%id%", id)), rows);
    }
    private void save(Crate c) { cm().saveCrate(c); }
    private void back(Menu m, int slot, Runnable r) { m.set(slot, ic(Material.ARROW, "<yellow>« Retour"), e -> r.run()); }
    private boolean prompt(ClickType t) { return t.isShiftClick() || t == ClickType.DROP; }
    private Material mat(Material m) { return m.isItem() && !m.isAir() ? m : Material.CHEST; }

    private String[] listLore(String header, List<String> l, String hint) {
        List<String> out = new ArrayList<>();
        out.add("<gray>" + header);
        if (l.isEmpty()) out.add("<dark_gray>(vide)");
        for (String s : l) out.add("<dark_gray>- <reset>" + s);
        out.add("");
        out.add("<yellow>Clic gauche <gray>: ajouter " + hint);
        out.add("<yellow>Clic droit <gray>: retirer la dernière");
        out.add("<yellow>Shift <gray>: tout vider");
        return out.toArray(new String[0]);
    }

    private void editList(Player p, List<String> l, ClickType t, String promptText, Runnable after) {
        if (t.isShiftClick()) { l.clear(); after.run(); }
        else if (t.isRightClick()) { if (!l.isEmpty()) l.removeLast(); after.run(); }
        else Prompt.ask(p, promptText, s -> { l.add(s); after.run(); });
    }

    private <T> void paged(Menu m, List<T> list, int page, Function<T, ItemStack> icon, BiConsumer<T, ClickType> click, IntConsumer reopen) {
        int per = 45, pages = Math.max(1, (list.size() + per - 1) / per);
        int pg = Math.max(0, Math.min(page, pages - 1));
        m.fillRange(45, 54, glass());
        for (int i = 0; i < per; i++) {
            int idx = pg * per + i;
            if (idx >= list.size()) break;
            T t = list.get(idx);
            m.set(i, icon.apply(t), e -> click.accept(t, e.getClick()));
        }
        if (pg > 0) m.set(45, ic(Material.ARROW, "<yellow>« Page précédente"), e -> reopen.accept(pg - 1));
        if (pg < pages - 1) m.set(53, ic(Material.ARROW, "<yellow>Page suivante »"), e -> reopen.accept(pg + 1));
    }

    // ================= MENU PRINCIPAL =================
    public void main(Player p) {
        Menu m = menu("title.main", "<dark_gray>TripleCrates • Éditeur", 3, "");
        m.fill(glass());
        m.set(11, ic(Material.CHEST, "<gold>Caisses", "<gray>Créer, modifier, supprimer"), e -> crateList(p, 0));
        m.set(13, ic(Material.TRIPWIRE_HOOK, "<yellow>Clés", "<gray>Créer des clés et les lier aux caisses"), e -> keyList(p, 0));
        m.set(15, ic(Material.NETHER_STAR, "<light_purple>Raretés", "<gray>Noms et couleurs des raretés"), e -> rarityList(p, 0));
        m.set(22, ic(Material.REPEATER, "<green>Recharger depuis les fichiers", "<gray>Relit config, messages, caisses, clés"), e -> {
            pl.reloadAll();
            Text.send(p, "reloaded");
            main(p);
        });
        m.set(26, ic(Material.BARRIER, "<red>Fermer"), e -> p.closeInventory());
        m.open(p);
    }

    // ================= CAISSES =================
    public void crateList(Player p, int page) {
        Menu m = menu("title.crates", "<dark_gray>Caisses", 6, "");
        paged(m, new ArrayList<>(cm().crates.values()), page,
                c -> ic(mat(c.block), c.name, "<gray>ID : <white>" + c.id, "<gray>Récompenses : <white>" + c.rewards.size(),
                        "<gray>Clé : <white>" + (c.keyId == null ? "aucune" : c.keyId), "", "<yellow>Clic <gray>: modifier"),
                (c, t) -> crateMenu(p, c), pg -> crateList(p, pg));
        back(m, 49, () -> main(p));
        m.set(50, ic(Material.EMERALD, "<green>+ Créer une caisse"), e -> Prompt.ask(p, "ID de la nouvelle caisse (a-z, 0-9, - _)", s -> {
            String id = CrateManager.sanitize(s);
            if (id == null) { Text.send(p, "invalid-id"); return; }
            if (cm().crates.containsKey(id)) { Text.send(p, "already-exists"); return; }
            Crate c = new Crate(id);
            save(c);
            crateMenu(p, c);
        }));
        m.open(p);
    }

    public void crateMenu(Player p, Crate c) {
        Menu m = menu("title.crate", "<dark_gray>Caisse • %id%", 4, c.id);
        m.fill(glass());

        m.set(10, ic(Material.NAME_TAG, "<yellow>Nom affiché", "<gray>Actuel : <reset>" + c.name, "", "<yellow>Clic <gray>: modifier"),
                e -> Prompt.ask(p, "Nouveau nom de la caisse (MiniMessage ou &)", s -> { c.name = s; save(c); crateMenu(p, c); }));

        m.set(11, ic(Material.WRITABLE_BOOK, "<yellow>Description", listLore("Lignes :", c.description, "une ligne")),
                e -> editList(p, c.description, e.getClick(), "Ligne de description", () -> { save(c); crateMenu(p, c); }));

        m.set(12, ic(mat(c.block), "<yellow>Bloc de la caisse", "<gray>Actuel : <white>" + c.block.name(), "",
                        "<yellow>Clic <gray>: utiliser le bloc en main", "<gray>(main vide = saisir le nom)"),
                e -> {
                    ItemStack h = p.getInventory().getItemInMainHand();
                    if (!h.isEmpty() && h.getType().isBlock()) { c.block = h.getType(); save(c); crateMenu(p, c); return; }
                    Prompt.ask(p, "Nom du bloc (CHEST, BARREL, BARRIER...)", s -> {
                        Material mm = Material.matchMaterial(s.trim());
                        if (mm != null && mm.isBlock()) { c.block = mm; save(c); } else Text.send(p, "invalid-item");
                        crateMenu(p, c);
                    });
                });

        m.set(13, ic(Material.TRIPWIRE_HOOK, "<yellow>Clé liée", "<gray>Actuelle : <white>" + (c.keyId == null ? "aucune" : c.keyId), "",
                "<yellow>Clic gauche/droit <gray>: changer"), e -> {
            List<String> ids = new ArrayList<>();
            ids.add(null);
            ids.addAll(cm().keys.keySet());
            int i = ids.indexOf(c.keyId);
            i = e.getClick().isRightClick() ? (i - 1 + ids.size()) % ids.size() : (i + 1) % ids.size();
            c.keyId = ids.get(i);
            save(c);
            crateMenu(p, c);
        });

        m.set(14, ic(Material.ARMOR_STAND, "<yellow>Modèle 3D de la caisse",
                "<gray>Type : <white>" + c.model.type, "<gray>ID : <white>" + (c.model.id.isBlank() ? "-" : c.model.id),
                "<gray>Décalage Y : <white>" + Text.num(c.model.yOffset),
                "<gray>Hitbox : <white>" + Text.num(c.model.width) + " x " + Text.num(c.model.height), "",
                "<yellow>Clic gauche <gray>: changer le type", "<yellow>Clic droit <gray>: ID (MythicMobs / ModelEngine)",
                "<yellow>Shift-gauche <gray>: décalage Y", "<yellow>Shift-droit <gray>: hitbox (largeur hauteur)"), e -> {
            ClickType t = e.getClick();
            if (t == ClickType.LEFT) {
                c.model.type = MODEL_TYPES.get((MODEL_TYPES.indexOf(c.model.type) + 1) % MODEL_TYPES.size());
                save(c);
                cm().removeAllVisuals();
                cm().ensureAllVisuals();
                crateMenu(p, c);
            } else if (t == ClickType.RIGHT) {
                Prompt.ask(p, "ID du modèle (nom du mob MythicMobs ou blueprint ModelEngine)", s -> { c.model.id = s.trim(); applyModel(c); crateMenu(p, c); });
            } else if (t == ClickType.SHIFT_LEFT) {
                Prompt.ask(p, "Décalage vertical du modèle (ex: 0.5)", s -> {
                    try { c.model.yOffset = Double.parseDouble(s.replace(',', '.')); applyModel(c); } catch (NumberFormatException ex) { Text.send(p, "invalid-number"); }
                    crateMenu(p, c);
                });
            } else if (t == ClickType.SHIFT_RIGHT) {
                Prompt.ask(p, "Hitbox : largeur hauteur (ex: 1.5 2)", s -> {
                    try {
                        String[] a = s.replace(',', '.').split("[ ;]+");
                        c.model.width = Double.parseDouble(a[0]);
                        c.model.height = Double.parseDouble(a[1]);
                        applyModel(c);
                    } catch (Exception ex) { Text.send(p, "invalid-number"); }
                    crateMenu(p, c);
                });
            }
        });

        m.set(15, ic(Material.FIREWORK_ROCKET, "<yellow>Animation", "<gray>Particules, sons, durées, coffres...", "", "<yellow>Clic <gray>: ouvrir"), e -> animMenu(p, c));
        m.set(16, ic(Material.ITEM_FRAME, "<yellow>Interface d'aperçu", "<gray>Titre, lignes, filler, infos affichées", "", "<yellow>Clic <gray>: ouvrir"), e -> previewSettings(p, c));
        m.set(20, ic(Material.DIAMOND, "<aqua>Récompenses", "<gray>" + c.rewards.size() + " récompense(s)", "", "<yellow>Clic <gray>: gérer"), e -> rewardList(p, c, 0));
        m.set(22, ic(Material.CHEST_MINECART, "<green>Recevoir l'item de la caisse", "<gray>À poser pour créer une caisse active"), e -> {
            p.getInventory().addItem(cm().crateItem(c)).values().forEach(o -> p.getWorld().dropItemNaturally(p.getLocation(), o));
        });
        m.set(24, ic(Material.SPYGLASS, "<green>Aperçu joueur"), e -> PreviewMenu.open(p, c, 0));
        m.set(31, ic(Material.BARRIER, "<red>Supprimer la caisse", "<gray>Retire aussi les caisses posées", "", "<red>Shift + clic <gray>pour confirmer"), e -> {
            if (!e.getClick().isShiftClick()) return;
            cm().deleteCrate(c);
            crateList(p, 0);
        });
        back(m, 27, () -> crateList(p, 0));
        m.open(p);
    }

    private void applyModel(Crate c) {
        save(c);
        cm().removeAllVisuals();
        cm().ensureAllVisuals();
    }

    // ================= ANIMATION =================
    public void animMenu(Player p, Crate c) {
        Menu m = menu("title.anim", "<dark_gray>Animation • %id%", 5, c.id);
        m.fill(glass());
        for (int i = 0; i < ANIM.size(); i++) {
            AS s = ANIM.get(i);
            String hint = switch (s.kind()) {
                case "N" -> "<yellow>Gauche/droit <gray>: +/- " + Text.num(s.step()) + "   <yellow>Shift <gray>: saisir";
                case "P", "S" -> "<yellow>Gauche/droit <gray>: changer   <yellow>Shift <gray>: saisir";
                case "I" -> "<yellow>Clic <gray>: item en main (ou saisir)";
                case "M" -> "<yellow>Gauche/droit <gray>: changer";
                default -> "<yellow>Clic <gray>: saisir";
            };
            m.set(ANIM_SLOTS[i], ic(s.icon(), "<yellow>" + s.label(), "<gray>Valeur : <white>" + c.str(s.key()), "", hint),
                    e -> animClick(p, c, s, e.getClick()));
        }
        back(m, 40, () -> crateMenu(p, c));
        m.open(p);
    }

    private void setAnim(Player p, Crate c, String key, Object val) {
        c.anim.put(key, val);
        save(c);
        animMenu(p, c);
    }

    private void animClick(Player p, Crate c, AS s, ClickType t) {
        String cur = c.str(s.key());
        switch (s.kind()) {
            case "P", "S" -> {
                if (prompt(t)) {
                    Prompt.ask(p, s.label() + " (clé minecraft, ex: flame)", v -> setAnim(p, c, s.key(), v.trim().toLowerCase()));
                    return;
                }
                List<String> l = s.kind().equals("P") ? PARTICLES : SOUNDS;
                int i = l.indexOf(cur);
                i = t.isRightClick() ? (i - 1 + l.size()) % l.size() : (i + 1) % l.size();
                setAnim(p, c, s.key(), l.get(i));
            }
            case "N" -> {
                if (prompt(t)) {
                    Prompt.ask(p, s.label() + " (nombre)", v -> {
                        try { setAnim(p, c, s.key(), clamp(Double.parseDouble(v.replace(',', '.')), s)); }
                        catch (NumberFormatException ex) { Text.send(p, "invalid-number"); animMenu(p, c); }
                    });
                    return;
                }
                double v = c.num(s.key()) + (t.isRightClick() ? -s.step() : s.step());
                setAnim(p, c, s.key(), clamp(v, s));
            }
            case "I" -> {
                ItemStack h = p.getInventory().getItemInMainHand();
                if (!h.isEmpty()) { setAnim(p, c, s.key(), ItemUtil.encode(h)); return; }
                Prompt.ask(p, "Item des coffres (CHEST, nexo:mon_coffre...)", v -> {
                    if (ItemUtil.resolve(v) == null) { Text.send(p, "invalid-item"); animMenu(p, c); } else setAnim(p, c, s.key(), v.trim());
                });
            }
            case "M" -> {
                int i = MODEL_TYPES.indexOf(cur.toUpperCase());
                setAnim(p, c, s.key(), MODEL_TYPES.get((Math.max(i, 0) + 1) % MODEL_TYPES.size()));
            }
            default -> Prompt.ask(p, s.label(), v -> setAnim(p, c, s.key(), v.trim()));
        }
    }

    private double clamp(double v, AS s) { return Math.max(s.min(), Math.min(s.max(), v)); }

    // ================= APERÇU =================
    public void previewSettings(Player p, Crate c) {
        Menu m = menu("title.preview", "<dark_gray>Aperçu • %id%", 3, c.id);
        m.fill(glass());
        m.set(10, ic(Material.NAME_TAG, "<yellow>Titre", "<gray>Actuel : <reset>" + c.previewTitle, "<gray>%crate% = nom de la caisse", "",
                "<gray>Astuce : colle ici le glyphe Nexo du GUI custom", "<yellow>Clic <gray>: modifier"),
                e -> Prompt.ask(p, "Titre de l'aperçu (MiniMessage, %crate%, glyphes)", s -> { c.previewTitle = s; save(c); previewSettings(p, c); }));
        m.set(11, ic(Material.LADDER, "<yellow>Nombre de lignes", "<gray>Actuel : <white>" + c.previewRows, "", "<yellow>Gauche/droit <gray>: +/-"), e -> {
            c.previewRows = Math.max(3, Math.min(6, c.previewRows + (e.getClick().isRightClick() ? -1 : 1)));
            save(c);
            previewSettings(p, c);
        });
        m.set(12, ic(Material.GRAY_STAINED_GLASS_PANE, "<yellow>Item de remplissage", "<gray>Actuel : <white>" + c.previewFiller, "",
                "<yellow>Clic <gray>: item en main (ou saisir, nexo:id ok)"), e -> {
            ItemStack h = p.getInventory().getItemInMainHand();
            if (!h.isEmpty()) { c.previewFiller = ItemUtil.encode(h); save(c); previewSettings(p, c); return; }
            Prompt.ask(p, "Item de remplissage", s -> {
                if (ItemUtil.resolve(s) == null) Text.send(p, "invalid-item"); else { c.previewFiller = s.trim(); save(c); }
                previewSettings(p, c);
            });
        });
        m.set(14, ic(Material.GOLD_NUGGET, "<yellow>Afficher les chances", "<gray>État : " + (c.showChance ? "<green>oui" : "<red>non")), e -> { c.showChance = !c.showChance; save(c); previewSettings(p, c); });
        m.set(15, ic(Material.AMETHYST_SHARD, "<yellow>Afficher les raretés", "<gray>État : " + (c.showRarity ? "<green>oui" : "<red>non")), e -> { c.showRarity = !c.showRarity; save(c); previewSettings(p, c); });
        m.set(16, ic(Material.SPYGLASS, "<green>Voir le résultat"), e -> PreviewMenu.open(p, c, 0));
        back(m, 22, () -> crateMenu(p, c));
        m.open(p);
    }

    // ================= RÉCOMPENSES =================
    private ItemStack rewardIcon(Crate c, Reward r) {
        return ItemUtil.withLore(r.displayItem(), "", "<gray>ID : <white>" + r.id, "<gray>Type : <white>" + r.type,
                "<gray>Rareté : " + cm().rarityName(r.rarity), "<gray>Poids : <white>" + Text.num(r.weight) + " <gray>(<yellow>" + Text.num(c.chance(r)) + "%<gray>)",
                "", "<yellow>Clic <gray>: modifier", "<red>Shift-droit <gray>: supprimer");
    }

    public void rewardList(Player p, Crate c, int page) {
        Menu m = menu("title.rewards", "<dark_gray>Récompenses • %id%", 6, c.id);
        paged(m, new ArrayList<>(c.rewards.values()), page, r -> rewardIcon(c, r), (r, t) -> {
            if (t == ClickType.SHIFT_RIGHT) { c.rewards.remove(r.id); save(c); rewardList(p, c, page); }
            else rewardMenu(p, c, r);
        }, pg -> rewardList(p, c, pg));
        back(m, 49, () -> crateMenu(p, c));
        m.set(50, ic(Material.EMERALD, "<green>+ Ajouter une récompense", "<gray>Tiens l'item en main (outil, item Nexo, item custom...)",
                "<gray>puis clique. Main vide = saisir un matériau / nexo:id"), e -> {
            ItemStack h = p.getInventory().getItemInMainHand();
            if (!h.isEmpty()) {
                Reward r = newReward(c);
                r.item = ItemUtil.encode(h);
                r.amount = h.getAmount();
                c.rewards.put(r.id, r);
                save(c);
                rewardMenu(p, c, r);
                return;
            }
            Prompt.ask(p, "Item de la récompense (ex: DIAMOND, nexo:rubis)", s -> {
                if (ItemUtil.resolve(s) == null) { Text.send(p, "invalid-item"); rewardList(p, c, page); return; }
                Reward r = newReward(c);
                r.item = s.trim();
                c.rewards.put(r.id, r);
                save(c);
                rewardMenu(p, c, r);
            });
        });
        m.open(p);
    }

    private Reward newReward(Crate c) {
        int n = c.rewards.size() + 1;
        while (c.rewards.containsKey("reward_" + n)) n++;
        Reward r = new Reward("reward_" + n);
        if (!cm().rarities.isEmpty() && !cm().rarities.containsKey(r.rarity)) r.rarity = cm().rarities.keySet().iterator().next();
        return r;
    }

    public void rewardMenu(Player p, Crate c, Reward r) {
        Menu m = menu("title.reward", "<dark_gray>Récompense • %id%", 4, r.id);
        m.fill(glass());
        Runnable again = () -> { save(c); rewardMenu(p, c, r); };

        m.set(10, ItemUtil.withLore(r.displayItem(), "", "<yellow>Clic <gray>: remplacer par l'item en main",
                "<gray>(main vide = saisir un matériau / nexo:id)"), e -> {
            ItemStack h = p.getInventory().getItemInMainHand();
            if (!h.isEmpty()) { r.item = ItemUtil.encode(h); r.amount = h.getAmount(); again.run(); return; }
            Prompt.ask(p, "Item (ex: DIAMOND, nexo:rubis)", s -> {
                if (ItemUtil.resolve(s) == null) Text.send(p, "invalid-item"); else r.item = s.trim();
                again.run();
            });
        });

        m.set(11, ic(Material.PAPER, "<yellow>Quantité", "<gray>Actuelle : <white>" + r.amount, "",
                "<yellow>Gauche/droit <gray>: +/- 1", "<yellow>Shift <gray>: +/- 10"), e -> {
            int d = e.getClick().isShiftClick() ? 10 : 1;
            r.amount = Math.max(1, r.amount + (e.getClick().isRightClick() ? -d : d));
            again.run();
        });

        m.set(12, ic(Material.NAME_TAG, "<yellow>Nom affiché", "<gray>Actuel : <reset>" + (r.name == null ? "(nom de l'item)" : r.name), "",
                "<yellow>Gauche <gray>: modifier", "<yellow>Droit <gray>: réinitialiser"), e -> {
            if (e.getClick().isRightClick()) { r.name = null; again.run(); }
            else Prompt.ask(p, "Nom affiché de la récompense (MiniMessage ou &)", s -> { r.name = s; again.run(); });
        });

        m.set(13, ic(Material.WRITABLE_BOOK, "<yellow>Lore (affichage)", listLore("Lignes ajoutées :", r.lore, "une ligne")),
                e -> editList(p, r.lore, e.getClick(), "Ligne de lore", again));

        m.set(14, ic(Material.AMETHYST_SHARD, "<yellow>Rareté", "<gray>Actuelle : " + cm().rarityName(r.rarity), "", "<yellow>Gauche/droit <gray>: changer"), e -> {
            List<String> ids = new ArrayList<>(cm().rarities.keySet());
            if (ids.isEmpty()) return;
            int i = ids.indexOf(r.rarity);
            i = e.getClick().isRightClick() ? (i - 1 + ids.size()) % ids.size() : (i + 1) % ids.size();
            r.rarity = ids.get(i);
            again.run();
        });

        m.set(15, ic(Material.GOLD_NUGGET, "<yellow>Poids / chance", "<gray>Poids : <white>" + Text.num(r.weight), "<gray>Chance : <yellow>" + Text.num(c.chance(r)) + "%", "",
                "<yellow>Gauche/droit <gray>: +/- 1", "<yellow>Shift <gray>: +/- 10", "<yellow>Q (jeter) <gray>: saisir"), e -> {
            ClickType t = e.getClick();
            if (t == ClickType.DROP) {
                Prompt.ask(p, "Poids (nombre, ex: 12.5)", s -> {
                    try { r.weight = Math.max(0, Double.parseDouble(s.replace(',', '.'))); } catch (NumberFormatException ex) { Text.send(p, "invalid-number"); }
                    again.run();
                });
                return;
            }
            double d = t.isShiftClick() ? 10 : 1;
            r.weight = Math.max(0, r.weight + (t.isRightClick() ? -d : d));
            again.run();
        });

        m.set(16, ic(Material.COMPARATOR, "<yellow>Type de récompense", "<gray>Actuel : <white>" + r.type,
                "<gray>ITEM = donne l'item", "<gray>COMMAND = exécute les commandes", "", "<yellow>Clic <gray>: basculer"), e -> {
            r.type = r.type == Reward.Type.ITEM ? Reward.Type.COMMAND : Reward.Type.ITEM;
            again.run();
        });

        m.set(19, ic(Material.COMMAND_BLOCK, "<yellow>Commandes", listLore("Exécutées pour le joueur (%player%) :", r.commands, "une commande (sans /)")),
                e -> editList(p, r.commands, e.getClick(), "Commande (sans /). Placeholders : %player% %crate% %reward% ; préfixe [player] ou [console]", again));

        m.set(20, ic(Material.BELL, "<yellow>Annonce globale", "<gray>État : " + (r.broadcast ? "<green>oui" : "<red>non"), "", "<yellow>Clic <gray>: basculer"), e -> {
            r.broadcast = !r.broadcast;
            again.run();
        });

        m.set(22, ic(Material.SPYGLASS, "<green>Tester (te la donner)"), e -> r.give(p, c));
        m.set(31, ic(Material.BARRIER, "<red>Supprimer", "<red>Shift + clic <gray>pour confirmer"), e -> {
            if (!e.getClick().isShiftClick()) return;
            c.rewards.remove(r.id);
            save(c);
            rewardList(p, c, 0);
        });
        back(m, 27, () -> rewardList(p, c, 0));
        m.open(p);
    }

    // ================= CLÉS =================
    public void keyList(Player p, int page) {
        Menu m = menu("title.keys", "<dark_gray>Clés", 6, "");
        paged(m, new ArrayList<>(cm().keys.values()), page,
                k -> ItemUtil.withLore(k.build(1), "", "<gray>ID : <white>" + k.id, "<yellow>Clic <gray>: modifier"),
                (k, t) -> keyMenu(p, k), pg -> keyList(p, pg));
        back(m, 49, () -> main(p));
        m.set(50, ic(Material.EMERALD, "<green>+ Créer une clé"), e -> Prompt.ask(p, "ID de la nouvelle clé (a-z, 0-9, - _)", s -> {
            String id = CrateManager.sanitize(s);
            if (id == null) { Text.send(p, "invalid-id"); return; }
            if (cm().keys.containsKey(id)) { Text.send(p, "already-exists"); return; }
            CrateKey k = new CrateKey(id);
            cm().keys.put(id, k);
            cm().saveKeys();
            keyMenu(p, k);
        }));
        m.open(p);
    }

    public void keyMenu(Player p, CrateKey k) {
        Menu m = menu("title.key", "<dark_gray>Clé • %id%", 4, k.id);
        m.fill(glass());
        Runnable again = () -> { cm().saveKeys(); keyMenu(p, k); };

        m.set(10, ItemUtil.withLore(k.build(1), "", "<yellow>Clic <gray>: utiliser l'item en main comme base", "<gray>(item Nexo ok ; main vide = saisir)"), e -> {
            ItemStack h = p.getInventory().getItemInMainHand();
            if (!h.isEmpty()) { k.item = ItemUtil.encode(h); again.run(); return; }
            Prompt.ask(p, "Item de la clé (ex: TRIPWIRE_HOOK, nexo:cle_rare)", s -> {
                if (ItemUtil.resolve(s) == null) Text.send(p, "invalid-item"); else k.item = s.trim();
                again.run();
            });
        });
        m.set(11, ic(Material.NAME_TAG, "<yellow>Nom", "<gray>Actuel : <reset>" + k.name, "", "<yellow>Clic <gray>: modifier"),
                e -> Prompt.ask(p, "Nom de la clé (MiniMessage ou &)", s -> { k.name = s; again.run(); }));
        m.set(12, ic(Material.WRITABLE_BOOK, "<yellow>Lore", listLore("Lignes (%rarity% disponible) :", k.lore, "une ligne")),
                e -> editList(p, k.lore, e.getClick(), "Ligne de lore (%rarity% = rareté de la clé)", again));
        m.set(13, ic(Material.AMETHYST_SHARD, "<yellow>Rareté de la clé", "<gray>Actuelle : <reset>" + k.rarity, "", "<yellow>Clic <gray>: modifier"),
                e -> Prompt.ask(p, "Rareté affichée de la clé (MiniMessage)", s -> { k.rarity = s; again.run(); }));
        m.set(14, ic(Material.ENCHANTED_BOOK, "<yellow>Brillance", "<gray>État : " + (k.glow ? "<green>oui" : "<red>non")), e -> { k.glow = !k.glow; again.run(); });
        m.set(15, ic(Material.CHEST, "<yellow>Caisses liées", "<gray>Choisir les caisses ouvertes par cette clé", "", "<yellow>Clic <gray>: gérer"), e -> keyLinks(p, k, 0));
        m.set(16, ic(Material.EMERALD, "<green>Recevoir la clé"), e -> p.getInventory().addItem(k.build(1)).values().forEach(o -> p.getWorld().dropItemNaturally(p.getLocation(), o)));
        m.set(31, ic(Material.BARRIER, "<red>Supprimer la clé", "<red>Shift + clic <gray>pour confirmer"), e -> {
            if (!e.getClick().isShiftClick()) return;
            cm().keys.remove(k.id);
            cm().saveKeys();
            for (Crate c : cm().crates.values()) if (k.id.equals(c.keyId)) { c.keyId = null; save(c); }
            keyList(p, 0);
        });
        back(m, 27, () -> keyList(p, 0));
        m.open(p);
    }

    private void keyLinks(Player p, CrateKey k, int page) {
        Menu m = menu("title.keylinks", "<dark_gray>Caisses liées • %id%", 6, k.id);
        paged(m, new ArrayList<>(cm().crates.values()), page, c -> {
            boolean on = k.id.equals(c.keyId);
            return ic(mat(c.block), c.name, "<gray>ID : <white>" + c.id, "<gray>Lien : " + (on ? "<green>actif" : "<red>inactif"),
                    c.keyId != null && !on ? "<gray>(clé actuelle : <white>" + c.keyId + "<gray>)" : "", "<yellow>Clic <gray>: lier / délier");
        }, (c, t) -> {
            c.keyId = k.id.equals(c.keyId) ? null : k.id;
            save(c);
            keyLinks(p, k, page);
        }, pg -> keyLinks(p, k, pg));
        back(m, 49, () -> keyMenu(p, k));
        m.open(p);
    }

    // ================= RARETÉS =================
    public void rarityList(Player p, int page) {
        Menu m = menu("title.rarities", "<dark_gray>Raretés", 6, "");
        paged(m, new ArrayList<>(cm().rarities.keySet()), page,
                id -> ic(Material.PAPER, cm().rarityName(id), "<gray>ID : <white>" + id, "", "<yellow>Clic <gray>: renommer / recolorer", "<red>Shift-droit <gray>: supprimer"),
                (id, t) -> {
                    if (t == ClickType.SHIFT_RIGHT) { cm().rarities.remove(id); cm().saveRarities(); rarityList(p, page); return; }
                    Prompt.ask(p, "Nom affiché de la rareté (ex: <gold><bold>LÉGENDAIRE)", s -> { cm().rarities.put(id, s); cm().saveRarities(); rarityList(p, page); });
                }, pg -> rarityList(p, pg));
        back(m, 49, () -> main(p));
        m.set(50, ic(Material.EMERALD, "<green>+ Créer une rareté"), e -> Prompt.ask(p, "ID de la rareté (a-z, 0-9, - _)", s -> {
            String id = CrateManager.sanitize(s);
            if (id == null) { Text.send(p, "invalid-id"); return; }
            if (cm().rarities.containsKey(id)) { Text.send(p, "already-exists"); return; }
            Prompt.ask(p, "Nom affiché (MiniMessage, ex: <green>Inhabituel)", n -> { cm().rarities.put(id, n); cm().saveRarities(); rarityList(p, 0); });
        }));
        m.open(p);
    }
}
