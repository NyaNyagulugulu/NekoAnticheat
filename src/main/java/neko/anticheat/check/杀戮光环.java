package neko.anticheat.check;

import neko.anticheat.Anticheat;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.bukkit.Bukkit.getServer;

public class 杀戮光环 implements Listener {

    private final Anticheat plugin;
    private final ConcurrentHashMap<Player, Integer> vlMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Player, Long> lastAttackMap = new ConcurrentHashMap<>();
    private final Map<String, Float> lastYawMap = new HashMap<>();
    private final Map<String, UUID> lastTargetMap = new HashMap<>();

    public 杀戮光环(Anticheat plugin) {
        this.plugin = plugin;
        startVLReduceTask();
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        if (!plugin.getConfig().getBoolean("detection.killaura.enabled")) return;

        long now = System.currentTimeMillis();
        lastAttackMap.put(attacker, now);

        String key = attacker.getName();
        float currentYaw = attacker.getLocation().getYaw();
        float lastYaw = lastYawMap.getOrDefault(key, currentYaw);
        float yawDiff = Math.abs(currentYaw - lastYaw);
        lastYawMap.put(key, currentYaw);

        UUID victimId = victim.getUniqueId();
        UUID lastTargetId = lastTargetMap.getOrDefault(key, null);
        lastTargetMap.put(key, victimId);

        boolean sameTarget = lastTargetId != null && lastTargetId.equals(victimId);

        // 引入 Raytrace 精准对准判断
        Location attackerEye = attacker.getEyeLocation();
        Vector direction = attackerEye.getDirection();
        Location victimEye = victim.getEyeLocation();
        Vector toVictim = victimEye.toVector().subtract(attackerEye.toVector()).normalize();
        double dotProduct = direction.dot(toVictim);

        boolean badAim = dotProduct < 0.95; // 视角未对准

        // 检测攻击间隔（500ms 内多次攻击）
        long lastTime = lastAttackMap.getOrDefault(attacker, 0L);
        long interval = now - lastTime;
        boolean tooFast = interval < 500;

        if (sameTarget && yawDiff > 60.0f && badAim && tooFast && event.getDamage() > 0.1) {
            int vl = vlMap.getOrDefault(attacker, 0)
                    + plugin.getConfig().getInt("detection.killaura.rotation-vl", 1);
            vlMap.put(attacker, vl);

            int limit = plugin.getConfig().getInt("detection.killaura.vl-limit", 5);
            String alert = "§d[Neko反作弊] §f玩家 §c" + attacker.getName()
                    + " §f疑似 §6KillAura（视角偏离目标+短时间高频攻击）§f，目前 §dVL §f= §c" + vl + " §7/ §a" + limit;

            sendColoredMessage(new String[]{alert});
            for (Player admin : Bukkit.getOnlinePlayers()) {
                if (admin.hasPermission("nac.admin")) {
                    admin.sendMessage(alert);
                }
            }

            if (vl >= limit) {
                vlMap.remove(attacker);
                String cmd = plugin.getConfig().getString("detection.killaura.command",
                        "kick {player} 检测到杀戮光环！");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", attacker.getName()));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        String name = event.getPlayer().getName();
        vlMap.remove(event.getPlayer());
        lastAttackMap.remove(event.getPlayer());
        lastYawMap.remove(name);
        lastTargetMap.remove(name);
    }

    private void startVLReduceTask() {
        int interval = plugin.getConfig().getInt("detection.killaura.vl", 5);
        long ticks = interval * 20L;
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<Player, Integer> entry : new HashMap<>(vlMap).entrySet()) {
                    Player player = entry.getKey();
                    int vl = entry.getValue();
                    if (vl > 0) {
                        vlMap.put(player, vl - 1);
                    }
                }
            }
        }.runTaskTimer(plugin, ticks, ticks);
    }

    private void sendColoredMessage(String[] messages) {
        for (String message : messages) {
            getServer().getConsoleSender().sendMessage(
                    ChatColor.translateAlternateColorCodes('&',
                            "&8[&dNeko反作弊&8] &r" + message)
            );
        }
    }
}
