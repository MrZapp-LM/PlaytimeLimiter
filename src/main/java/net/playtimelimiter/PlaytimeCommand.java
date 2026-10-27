package net.playtimelimiter;

import static net.playtimelimiter.PlaytimeLimiter.format;
import static net.playtimelimiter.PlaytimeLimiter.mm;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class PlaytimeCommand implements BasicCommand {
    private static final String ADMIN = "playtimelimiter.admin";
    private static final List<String> SUBCOMMANDS = List.of("set", "remove", "give", "reset", "info", "list", "reload");
    private static final List<String> UNLIMITED_WORDS = List.of("unlimited", "none", "off");
    private static final Pattern DURATION = Pattern.compile("(?:(\\d+)h)?(?:(\\d+)m?)?");

    private final PlaytimeLimiter plugin;

    PlaytimeCommand(PlaytimeLimiter plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length == 0 || !sender.hasPermission(ADMIN)) {
            showOwn(sender);
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("list")) {
            list(sender);
            return;
        }
        if (sub.equals("reload")) {
            plugin.reloadSettings();
            sender.sendMessage(mm("<green>PlaytimeLimiter config reloaded."));
            return;
        }
        if (!SUBCOMMANDS.contains(sub) || args.length < 2) {
            usage(sender);
            return;
        }
        Target target = resolve(args[1]);
        if (target == null) {
            sender.sendMessage(mm("<red>Unknown player <white>" + args[1] + "</white> (they need to have joined once)."));
            return;
        }
        Entry entry = plugin.entryFor(target.uuid, target.name);

        switch (sub) {
            case "set" -> {
                Integer minutes = args.length < 3 ? null
                        : UNLIMITED_WORDS.contains(args[2].toLowerCase(Locale.ROOT)) ? Entry.UNLIMITED
                        : parseMinutes(args[2]);
                if (minutes == null) {
                    sender.sendMessage(mm("<red>Usage: /playtime set <player> <minutes|1h30m|unlimited>"));
                    return;
                }
                if (entry == null) {
                    plugin.entries.put(target.uuid, new Entry(target.name, minutes, 0, plugin.settings().today()));
                } else {
                    synchronized (entry) {
                        entry.limitMinutes = minutes;
                        entry.name = target.name;
                    }
                }
                plugin.save();
                sender.sendMessage(mm(minutes == Entry.UNLIMITED
                        ? "<green><white>" + target.name + "</white> is now exempt from all playtime limits."
                        : "<green>Daily limit for <white>" + target.name + "</white> set to <gold>"
                                + format(minutes * 60L) + "</gold>."));
            }
            case "remove" -> {
                if (entry == null || entry.isDefault()) {
                    sender.sendMessage(mm("<yellow>" + target.name + " has no individual limit."));
                    return;
                }
                synchronized (entry) {
                    entry.limitMinutes = Entry.DEFAULT;
                }
                plugin.save();
                int def = plugin.settings().defaultLimit();
                sender.sendMessage(mm("<green>Removed the individual limit for <white>" + target.name + "</white>. "
                        + (def > 0 ? "The default limit of <gold>" + format(def * 60L) + "</gold> applies now."
                                : "They can play without a limit.")));
            }
            case "give" -> {
                Integer minutes = args.length >= 3 ? parseMinutes(args[2]) : null;
                if (minutes == null) {
                    sender.sendMessage(mm("<red>Usage: /playtime give <player> <minutes|1h30m>"));
                    return;
                }
                if (noLimit(sender, target, entry)) return;
                synchronized (entry) {
                    entry.rollDay(plugin.settings());
                    entry.usedSeconds -= minutes * 60L;
                }
                plugin.save();
                sender.sendMessage(mm("<green>Gave <white>" + target.name + "</white> <gold>" + format(minutes * 60L)
                        + "</gold> extra today. Left: <gold>" + format(entry.remainingSeconds(plugin.settings()))));
            }
            case "reset" -> {
                if (noLimit(sender, target, entry)) return;
                synchronized (entry) {
                    entry.rollDay(plugin.settings());
                    entry.usedSeconds = 0;
                }
                plugin.save();
                sender.sendMessage(mm("<green>Reset today's playtime for <white>" + target.name + "</white>."));
            }
            case "info" -> {
                if (noLimit(sender, target, entry)) return;
                sender.sendMessage(status(target.name, entry));
            }
        }
    }

    private void showOwn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            usage(sender);
            return;
        }
        Entry entry = plugin.entryFor(player.getUniqueId(), player.getName());
        if (entry == null || !entry.limited(plugin.settings())) {
            player.sendMessage(mm("<green>You have no playtime limit."));
        } else {
            player.sendMessage(status("You", entry));
        }
        if (player.hasPermission(ADMIN)) usage(player);
    }

    private void list(CommandSender sender) {
        int def = plugin.settings().defaultLimit();
        sender.sendMessage(mm(def > 0
                ? "<gold>Default limit: <white>" + format(def * 60L) + "</white> for everyone without an individual limit."
                : "<gold>Default limit: <gray>off"));
        List<Entry> individual = plugin.entries.values().stream().filter(e -> !e.isDefault()).toList();
        if (individual.isEmpty()) {
            sender.sendMessage(mm("<yellow>No players have an individual limit."));
            return;
        }
        sender.sendMessage(mm("<gold>Individual limits:"));
        for (Entry entry : individual) {
            sender.sendMessage(entry.limited(plugin.settings())
                    ? status(entry.name, entry)
                    : mm("<gray>" + entry.name + ": <aqua>unlimited"));
        }
    }

    private net.kyori.adventure.text.Component status(String who, Entry entry) {
        long limit = entry.limitSeconds(plugin.settings());
        long left = entry.remainingSeconds(plugin.settings());
        long used = Math.max(0, limit - left);
        return mm("<gray>" + who + ": <white>" + format(used) + "</white> of <white>" + format(limit)
                + (entry.isDefault() ? "</white> (default)" : "</white>") + " used today, <" + (left > 0 ? "green" : "red") + ">" + format(left) + " left");
    }

    private boolean noLimit(CommandSender sender, Target target, Entry entry) {
        if (entry != null && entry.limited(plugin.settings())) return false;
        if (entry != null && entry.limitMinutes == Entry.UNLIMITED) {
            sender.sendMessage(mm("<aqua>" + target.name + " is exempt from all playtime limits."));
            return true;
        }
        sender.sendMessage(mm("<yellow>" + target.name + " has no limit. Set one with /playtime set."));
        return true;
    }

    private static void usage(CommandSender sender) {
        sender.sendMessage(mm("""
                <gold>/playtime</gold> <gray>- show your own playtime
                <gold>/playtime set <player> <time></gold> <gray>- set an individual limit (90, 2h, 1h30m or unlimited)
                <gold>/playtime remove <player></gold> <gray>- remove the individual limit (default applies)
                <gold>/playtime give <player> <time></gold> <gray>- extra time for today only
                <gold>/playtime reset <player></gold> <gray>- reset today's used time
                <gold>/playtime info <player></gold> <gray>- show a player's status
                <gold>/playtime list</gold> <gray>- show the default and individual limits
                <gold>/playtime reload</gold> <gray>- reload config.yml"""));
    }

    /** Parses "90", "90m", "2h" or "1h30m" into minutes; null if invalid. */
    static Integer parseMinutes(String input) {
        Matcher m = DURATION.matcher(input.toLowerCase(Locale.ROOT));
        if (input.isEmpty() || !m.matches() || (m.group(1) == null && m.group(2) == null)) return null;
        long minutes = (m.group(1) == null ? 0 : Long.parseLong(m.group(1)) * 60)
                + (m.group(2) == null ? 0 : Long.parseLong(m.group(2)));
        return minutes > 0 && minutes <= 24 * 60 ? (int) minutes : null;
    }

    private record Target(UUID uuid, String name) {}

    /** Finds a player by name: online, previously joined, or already in the limit list. */
    private Target resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return new Target(online.getUniqueId(), online.getName());
        for (Map.Entry<UUID, Entry> e : plugin.entries.entrySet()) {
            if (e.getValue().name.equalsIgnoreCase(name)) return new Target(e.getKey(), e.getValue().name);
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(name);
        if (offline != null && offline.getName() != null) return new Target(offline.getUniqueId(), offline.getName());
        return null;
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (!source.getSender().hasPermission(ADMIN)) return List.of();
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>();
        if (args.length <= 1) {
            options.addAll(SUBCOMMANDS);
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("list") && !args[0].equalsIgnoreCase("reload")) {
            if (args[0].equalsIgnoreCase("set")) {
                Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
            }
            plugin.entries.values().stream().filter(e -> !e.isDefault()).forEach(e -> options.add(e.name));
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("give"))) {
            options.addAll(List.of("30m", "1h", "1h30m", "2h", "3h"));
            if (args[0].equalsIgnoreCase("set")) options.add("unlimited");
        }
        return options.stream().distinct().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(last)).toList();
    }
}
