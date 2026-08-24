package dev.peekhealth;

import net.kyori.adventure.text.Component;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PeekHealthPlugin extends JavaPlugin implements Listener {

    private enum Mode { LOOK, DAMAGE, BOTH }

    private record Shown(UUID target, long untilMillis) {}

    private Mode mode = Mode.BOTH;
    private double lookRange = 20;
    private long displayMillis = 3000;
    private String format = "";
    private int heartsLength = 10;
    private String heartFull = "❤";
    private String heartEmpty = "♡";
    private final Set<org.bukkit.entity.EntityType> blacklist = EnumSet.noneOf(org.bukkit.entity.EntityType.class);
    private final Set<String> disabledWorlds = new HashSet<>();

    private final Map<UUID, Shown> shown = new ConcurrentHashMap<>();
    private final Set<UUID> optedOut = ConcurrentHashMap.newKeySet();
    private File optOutFile;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        optOutFile = new File(getDataFolder(), "optout.yml");
        loadOptOuts();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::tick, 4L, 4L);
        getLogger().info("PeekHealth " + getDescription().getVersion()
                + " enabled (mode " + mode + ").");
    }

    private void loadSettings() {
        reloadConfig();
        try {
            mode = Mode.valueOf(getConfig().getString("mode", "BOTH").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            getLogger().warning("Unknown mode in config, falling back to BOTH.");
            mode = Mode.BOTH;
        }
        lookRange = getConfig().getDouble("look-range", 20);
        displayMillis = Math.max(500, (long) (getConfig().getDouble("display-seconds", 3) * 1000));
        format = getConfig().getString("format",
                "<white>{name}</white> <red>{hearts}</red> <gray>{health}/{max}</gray>");
        heartsLength = Math.max(1, getConfig().getInt("hearts-length", 10));
        heartFull = getConfig().getString("heart-full", "❤");
        heartEmpty = getConfig().getString("heart-empty", "♡");
        blacklist.clear();
        for (String s : getConfig().getStringList("blacklist")) {
            try {
                blacklist.add(org.bukkit.entity.EntityType.valueOf(s.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                getLogger().warning("Unknown entity type in blacklist: " + s);
            }
        }
        disabledWorlds.clear();
        getConfig().getStringList("disabled-worlds").forEach(w -> disabledWorlds.add(w.toLowerCase(Locale.ROOT)));
    }

    private void loadOptOuts() {
        optedOut.clear();
        if (!optOutFile.exists()) return;
        for (String key : YamlConfiguration.loadConfiguration(optOutFile).getStringList("opted-out")) {
            try {
                optedOut.add(UUID.fromString(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveOptOuts() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("opted-out", optedOut.stream().map(UUID::toString).toList());
        try {
            yml.save(optOutFile);
        } catch (IOException e) {
            getLogger().warning("Could not save optout.yml: " + e.getMessage());
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Player player : getServer().getOnlinePlayers()) {
            if (optedOut.contains(player.getUniqueId())) continue;
            if (disabledWorlds.contains(player.getWorld().getName().toLowerCase(Locale.ROOT))) continue;
            if (!player.hasPermission("peekhealth.use")) continue;

            LivingEntity target = null;
            if (mode != Mode.DAMAGE) {
                target = rayTarget(player);
            }
            if (target != null) {
                shown.put(player.getUniqueId(), new Shown(target.getUniqueId(), now + displayMillis));
            }

            Shown current = shown.get(player.getUniqueId());
            if (current == null) continue;
            if (current.untilMillis() < now) {
                shown.remove(player.getUniqueId());
                continue;
            }
            Entity entity = getServer().getEntity(current.target());
            if (!(entity instanceof LivingEntity living) || living.isDead()
                    || !living.getWorld().equals(player.getWorld())) {
                shown.remove(player.getUniqueId());
                continue;
            }
            player.sendActionBar(render(living));
        }
    }

    private LivingEntity rayTarget(Player player) {
        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(), player.getEyeLocation().getDirection(), lookRange, 0.35,
                e -> e instanceof LivingEntity && !e.equals(player) && !blacklist.contains(e.getType()));
        if (result == null) return null;
        // Line of sight: don't show through walls.
        Entity hit = result.getHitEntity();
        if (hit instanceof LivingEntity living && player.hasLineOfSight(living)) return living;
        return null;
    }

    private Component render(LivingEntity living) {
        double health = Math.max(0, living.getHealth());
        double max = living.getAttribute(Attribute.MAX_HEALTH) != null
                ? living.getAttribute(Attribute.MAX_HEALTH).getValue() : health;
        int filled = max <= 0 ? 0 : (int) Math.round(health / max * heartsLength);
        filled = Math.min(heartsLength, Math.max(health > 0 ? 1 : 0, filled));
        String hearts = heartFull.repeat(filled) + heartEmpty.repeat(heartsLength - filled);
        String name = living instanceof Player p ? p.getName()
                : living.customName() != null
                        ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                                .serialize(living.customName())
                        : formatType(living.getType().name());
        String line = format
                .replace("{name}", name)
                .replace("{health}", trim(health))
                .replace("{max}", trim(max))
                .replace("{percent}", max <= 0 ? "0" : String.valueOf(Math.round(health / max * 100)))
                .replace("{hearts}", hearts);
        return Format.parse(line);
    }

    private String formatType(String enumName) {
        String[] parts = enumName.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private String trim(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value)
                : String.valueOf(Math.round(value * 10) / 10.0);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (mode == Mode.LOOK) return;
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        Player damager = null;
        if (event.getDamager() instanceof Player p) damager = p;
        else if (event.getDamager() instanceof Projectile proj
                && proj.getShooter() instanceof Player p) damager = p;
        if (damager == null || optedOut.contains(damager.getUniqueId())) return;
        if (blacklist.contains(living.getType())) return;
        if (disabledWorlds.contains(damager.getWorld().getName().toLowerCase(Locale.ROOT))) return;
        shown.put(damager.getUniqueId(),
                new Shown(living.getUniqueId(), System.currentTimeMillis() + displayMillis));
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command cmd,
                             String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("peekhealth.reload")) {
                sender.sendMessage(Component.text("[PeekHealth] No permission."));
                return true;
            }
            loadSettings();
            sender.sendMessage(Component.text("[PeekHealth] Config reloaded."));
            return true;
        }
        if (sender instanceof Player player) {
            if (optedOut.remove(player.getUniqueId())) {
                sender.sendMessage(Component.text("[PeekHealth] Health display enabled."));
            } else {
                optedOut.add(player.getUniqueId());
                shown.remove(player.getUniqueId());
                sender.sendMessage(Component.text("[PeekHealth] Health display disabled."));
            }
            saveOptOuts();
        } else {
            sender.sendMessage(Component.text("[PeekHealth] /peekhealth reload — or run /peekhealth as a player to toggle."));
        }
        return true;
    }
}
