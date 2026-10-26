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
    // &c  §c  &#RRGGBB  &x&R&R&G&G&B&B
    private static final Pattern LEGACY = Pattern.compile("(?i)[&\u00a7](?:x(?:[&\u00a7][0-9a-f]){6}|#[0-9a-f]{6}|[0-9a-fk-or])");
    // #RRGGBB "nu" (pas dans une balise <#...>, <gradient:#...>, une URL, ni collé à un mot)
    private static final Pattern BARE_HEX = Pattern.compile("(?<![<:\\w&\u00a7#/])#([0-9a-fA-F]{6})(?![0-9a-zA-Z])");
    private static final DecimalFormat DF = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));

    private Text() {}

    /** Texte brut -> Component. Accepte MiniMessage, codes &/§ (&3, &l...), &#RRGGBB, &x&R&R&G&G&B&B, #RRGGBB et <#RRGGBB>. */
    public static Component parse(String s) { return MM.deserialize(preprocess(s)); }
    public static Component item(String s) { return parse(s).decoration(TextDecoration.ITALIC, false); }
    public static Component parseWith(String s, TagResolver... r) {
        return MM.deserialize(preprocess(s), r).decoration(TextDecoration.ITALIC, false);
    }
    public static String plain(Component c) { return PlainTextComponentSerializer.plainText().serialize(c); }
    public static String num(double d) { return DF.format(d); }

    /** Ancien nom, conservé. */
    public static String legacyToMini(String s) { return preprocess(s); }

    private static String legacyName(char c) {
        return switch (c) {
            case '0' -> "black"; case '1' -> "dark_blue"; case '2' -> "dark_green"; case '3' -> "dark_aqua";
            case '4' -> "dark_red"; case '5' -> "dark_purple"; case '6' -> "gold"; case '7' -> "gray";
            case '8' -> "dark_gray"; case '9' -> "blue"; case 'a' -> "green"; case 'b' -> "aqua";
            case 'c' -> "red"; case 'd' -> "light_purple"; case 'e' -> "yellow"; case 'f' -> "white";
            case 'k' -> "obfuscated"; case 'l' -> "bold"; case 'm' -> "strikethrough"; case 'n' -> "underlined";
            case 'o' -> "italic"; default -> "reset";
        };
    }

    /**
     * Convertit les codes couleur (&3, &l, &#ff8800, &x&f&f&8&8&0&0, #ff8800) en MiniMessage.
     * Comme en jeu, une couleur annule les styles précédents (gras, etc.). Sans effet sur du MiniMessage pur.
     */
    public static String preprocess(String s) {
        if (s == null || s.isEmpty()) return "";
        if (s.indexOf('&') < 0 && s.indexOf('\u00a7') < 0 && s.indexOf('#') < 0) return s;
        s = BARE_HEX.matcher(s).replaceAll("<#$1>");
        var m = LEGACY.matcher(s);
        StringBuilder out = new StringBuilder();
        boolean styled = false;
        int last = 0;
        while (m.find()) {
            out.append(s, last, m.start());
            last = m.end();
            String body = m.group().substring(1).toLowerCase(Locale.ROOT);
            String hex = null;
            if (body.startsWith("x")) hex = body.substring(1).replaceAll("[&\u00a7]", "");
            else if (body.startsWith("#")) hex = body.substring(1);
            if (hex != null) {
                out.append(styled ? "<reset>" : "").append("<#").append(hex).append('>');
                styled = false;
                continue;
            }
            char c = body.charAt(0);
            String name = legacyName(c);
            if (c == 'r') { out.append("<reset>"); styled = false; }
            else if ("klmno".indexOf(c) >= 0) { out.append('<').append(name).append('>'); styled = true; }
            else { out.append(styled ? "<reset>" : "").append('<').append(name).append('>'); styled = false; }
        }
        out.append(s, last, s.length());
        return out.toString();
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
