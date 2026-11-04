package org.example.sawervatoryy.mirageAuction.manager;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.example.sawervatoryy.mirageAuction.MirageAuction;
import org.example.sawervatoryy.mirageAuction.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Messages {

    private final MirageAuction plugin;

    public Messages(MirageAuction plugin) {
        this.plugin = plugin;
    }

    public String get(String key, Map<String, String> ph) {
        String tmpl = plugin.configs().messages().getString(key);
        if (tmpl == null) return "§c[MirageAuction] нет сообщения: " + key;
        String prefix = plugin.configs().messages().getString("prefix", "");
        tmpl = tmpl.replace("{prefix}", prefix);
        return Text.color(Text.apply(tmpl, ph));
    }

    public String get(String key, String... kv) {
        return get(key, Text.map(kv));
    }

    public Component component(String key, String... kv) {
        return Text.fromColored(get(key, kv));
    }

    public void send(CommandSender to, String key, String... kv) {
        if (to == null) return;
        String s = get(key, kv);
        if (s.isBlank()) return;
        to.sendMessage(Text.fromColored(s));
    }

    public void sendRaw(CommandSender to, String colored) {
        if (to != null && colored != null && !colored.isBlank()) to.sendMessage(Text.fromColored(colored));
    }

    public List<String> list(String key, String... kv) {
        List<String> out = new ArrayList<>();
        Map<String, String> ph = Text.map(kv);
        String prefix = plugin.configs().messages().getString("prefix", "");
        for (String l : plugin.configs().messages().getStringList(key)) {
            out.add(Text.color(Text.apply(l.replace("{prefix}", prefix), ph)));
        }
        return out;
    }
}
