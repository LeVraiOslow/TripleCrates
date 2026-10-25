package fr.triplecrates;

import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class TripleCratesPlugin extends JavaPlugin {
    private static TripleCratesPlugin instance;
    public NamespacedKey K_KEY, K_CRATE_ITEM, K_LOC, K_SESSION;
    private YamlConfiguration messages;
    private CrateManager crates;
    private Editor editor;

    public static TripleCratesPlugin get() { return instance; }
    public YamlConfiguration messages() { return messages; }
    public CrateManager crates() { return crates; }
    public Editor editor() { return editor; }

    @Override
    public void onEnable() {
        instance = this;
        K_KEY = new NamespacedKey(this, "key");
        K_CRATE_ITEM = new NamespacedKey(this, "crate_item");
        K_LOC = new NamespacedKey(this, "crate_loc");
        K_SESSION = new NamespacedKey(this, "session_chest");

        saveDefaultConfig();
        for (String f : List.of("messages.yml", "rarities.yml", "keys.yml")) {
            if (!new File(getDataFolder(), f).exists()) saveResource(f, false);
        }
        if (!new File(getDataFolder(), "crates").exists()) saveResource("crates/example.yml", false);

        Hooks.init();
        loadMessages();
        crates = new CrateManager(this);
        crates.loadAll();
        editor = new Editor(this);

        var pm = getServer().getPluginManager();
        pm.registerEvents(new MenuListener(), this);
        pm.registerEvents(new Prompt(this), this);
        pm.registerEvents(new CrateListener(this), this);

        PluginCommand cmd = getCommand("crates");
        CratesCommand ex = new CratesCommand(this);
        cmd.setExecutor(ex);
        cmd.setTabCompleter(ex);

        getServer().getScheduler().runTask(this, () -> crates.ensureAllVisuals());
        getLogger().info("Nexo=" + Hooks.nexo + " MythicMobs=" + Hooks.mythic + " ModelEngine=" + Hooks.modelEngine);
    }

    @Override
    public void onDisable() {
        for (OpenSession s : new ArrayList<>(OpenSession.ACTIVE.values())) s.forceFinish();
        if (crates != null) crates.removeAllVisuals();
    }

    private void loadMessages() {
        messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
        var def = getResource("messages.yml");
        if (def != null) messages.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(def, StandardCharsets.UTF_8)));
    }

    public void reloadAll() {
        reloadConfig();
        loadMessages();
        crates.loadAll();
        crates.ensureAllVisuals();
    }

    public void later(Runnable r) { getServer().getScheduler().runTask(this, r); }
}
