package net.playtimelimiter;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlaytimeLimiter extends JavaPlugin implements Listener {
    final Map<UUID, Entry> entries = new ConcurrentHashMap<>();
    private volatile Settings settings;
    private File dataFile;
    private volatile boolean dirty;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSettings();
        dataFile = new File(getDataFolder(), "data.yml");
        load();
        getServer().getPluginManager().registerEvents(this, this);
        registerCommand("playtime", "Daily playtime limits", List.of("pt"), new PlaytimeCommand(this));
        getServer().getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (dirty) save();
        }, 1200L, 1200L);
    }

    @Override
    public void onDisable() {
        save();
    }

    void reloadSettings() {
        reloadConfig();
        settings = Settings.from(getConfig(), getLogger());
    }

    Settings settings() {
        return settings;
    }

    /** Runs every second: counts playtime for limited players who are online. */
    private void tick() {
        Settings s = settings;
        for (Player player : getServer().getOnlinePlayers()) {
            Entry entry = entries.get(player.getUniqueId());
            if (entry == null) continue;
            long left = entry.tick(s);
            dirty = true;
            if (left <= 0) {
                player.kick(kickMessage(entry));
            } else if (left % 60 == 0 && s.warnAt().contains(left / 60)) {
                player.sendMessage(message(s.warningMessage(), entry));
            }
        }
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        Entry entry = entries.get(event.getUniqueId());
        if (entry != null && entry.remainingSeconds(settings) <= 0) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickMessage(entry));
        }
    }

    private Component kickMessage(Entry entry) {
        return message(settings.kickMessage(), entry);
    }

    /** Fills a config message's <limit>, <left> and <reset> placeholders for this player. */
    private Component message(String template, Entry entry) {
        Settings s = settings;
        return MiniMessage.miniMessage().deserialize(template,
                Placeholder.unparsed("limit", format(entry.limitSeconds(s))),
                Placeholder.unparsed("left", format(entry.remainingSeconds(s))),
                Placeholder.unparsed("reset", s.resetTime().toString()));
    }

    void markDirty() {
        dirty = true;
    }

    private void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : yaml.getKeys(false)) {
            ConfigurationSection s = yaml.getConfigurationSection(key);
            if (s == null) continue;
            String day = s.getString("day");
            entries.put(UUID.fromString(key), new Entry(
                    s.getString("name", key),
                    s.getInt("limit"),
                    s.getLong("used"),
                    day == null ? settings.today() : LocalDate.parse(day)));
        }
        getLogger().info("Loaded " + entries.size() + " playtime limit(s).");
    }

    synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        entries.forEach((uuid, entry) -> {
            synchronized (entry) {
                String key = uuid.toString();
                yaml.set(key + ".name", entry.name);
                yaml.set(key + ".limit", entry.limitMinutes);
                yaml.set(key + ".used", entry.usedSeconds);
                yaml.set(key + ".day", entry.day.toString());
            }
        });
        try {
            yaml.save(dataFile);
            dirty = false;
        } catch (IOException e) {
            getLogger().severe("Could not save " + dataFile + ": " + e.getMessage());
        }
    }

    static Component mm(String miniMessage) {
        return MiniMessage.miniMessage().deserialize(miniMessage);
    }

    /** Formats seconds as e.g. "1h 30m", "45m" or "20s". */
    static String format(long seconds) {
        if (seconds < 0) seconds = 0;
        long h = seconds / 3600, m = seconds % 3600 / 60;
        if (h > 0) return m > 0 ? h + "h " + m + "m" : h + "h";
        if (m > 0) return m + "m";
        return seconds + "s";
    }
}
