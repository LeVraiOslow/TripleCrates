package fr.triplecrates;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class Text {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Pattern LEGACY = Pattern.compile("&([0-9a-fk-or])");
    private static final DecimalFormat DF = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));

    private Text() {}

    public static Component parse(String s) { return MM.deserialize(s == null ? "" : s); }
    public static Component item(String s) { return parse(s).decoration(TextDecoration.ITALIC, false); }
    public static Component parseWith(String s, TagResolver... r) {
        return MM.deserialize(s == null ? "" : s, r).decoration(TextDecoration.ITALIC, false);
    }
    public static String plain(Component c) { return PlainTextComponentSerializer.plainText().serialize(c); }
    public static String num(double d) { return DF.format(d); }

    public static String legacyToMini(String s) {
        return LEGACY.matcher(s).replaceAll(r -> java.util.regex.Matcher.quoteReplacement(switch (r.group(1)) {
            case "0" -> "<black>"; case "1" -> "<dark_blue>"; case "2" -> "<dark_green>"; case "3" -> "<dark_aqua>";
            case "4" -> "<dark_red>"; case "5" -> "<dark_purple>"; case "6" -> "<gold>"; case "7" -> "<gray>";
            case "8" -> "<dark_gray>"; case "9" -> "<blue>"; case "a" -> "<green>"; case "b" -> "<aqua>";
            case "c" -> "<red>"; case "d" -> "<light_purple>"; case "e" -> "<yellow>"; case "f" -> "<white>";
            case "k" -> "<obfuscated>"; case "l" -> "<bold>"; case "m" -> "<strikethrough>"; case "n" -> "<underlined>";
            case "o" -> "<italic>"; default -> "<reset>";
        }));
    }

    public static String msgRaw(String key, String... kv) {
        var m = TripleCratesPlugin.get().messages();
        String s = m.getString(key, "<red>Missing message: " + key);
        s = s.replace("%prefix%", m.getString("prefix", ""));
        for (int i = 0; i + 1 < kv.length; i += 2) s = s.replace(kv[i], kv[i + 1]);
        return s;
    }

    public static void send(CommandSender to, String key, String... kv) { to.sendMessage(parse(msgRaw(key, kv))); }

    public static List<String> msgList(String key) { return TripleCratesPlugin.get().messages().getStringList(key); }

    /** Titre de GUI personnalisable via messages.yml (gui.<key>). */
    public static String gui(String key, String def) { return TripleCratesPlugin.get().messages().getString("gui." + key, def); }
}
