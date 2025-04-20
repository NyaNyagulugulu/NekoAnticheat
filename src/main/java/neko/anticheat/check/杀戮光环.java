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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.bukkit.Bukkit.getServer;

public class 杀戮光环 implements Listener {

    private final Anticheat plugin;
    private final ConcurrentHashMap<Player, Integer> vlMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Player, Long> lastAttackMap = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> attackHistory = new HashMap<>();
    private final Map<String, String> lastTargetMap = new HashMap<>();
    private final Map<String, Float> lastYawMap = new HashMap<>();
    private final Map<String, Long> lastIntervalMap = new HashMap<>();

    public 杀戮光环(Anticheat plugin) {
        this.plugin = plugin;
        startVLReduceTask();
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        if (!plugin.getConfig().getBoolean("detection.killaura.enabled")) return;

        String key = attacker.getName();
        long now = System.currentTimeMillis();
        lastAttackMap.put(attacker, now);

        // 记录攻击时间
        Deque<Long> hitTimestamps = attackHistory.computeIfAbsent(key, k -> new ArrayDeque<>());
        hitTimestamps.addLast(now);
        while (!hitTimestamps.isEmpty() && now - hitTimestamps.peekFirst() > 1000) {
            hitTimestamps.pollFirst();
        }

        // 检查攻击目标是否相同
        String lastTarget = lastTargetMap.getOrDefault(key, "");
        boolean sameTarget = lastTarget.equals(victim.getName());
        lastTargetMap.put(key, victim.getName());

        // 计算视角差
        float currentYaw = attacker.getLocation().getYaw();
        float lastYaw = lastYawMap.getOrDefault(key, currentYaw);
        float yawDiff = Math.abs(currentYaw - lastYaw);
        lastYawMap.put(key, currentYaw);

        // 计算攻击间隔一致性
        long lastInterval = lastIntervalMap.getOrDefault(key, now);
        long delta = now - lastInterval;
        lastIntervalMap.put(key, now);

        boolean consistentInterval = delta > 30 && delta < 300;
        boolean lowYawDiff = yawDiff < 3.0f;

        if (sameTarget && hitTimestamps.size() >= 6 && consistentInterval && lowYawDiff) {
            int vl = vlMap.getOrDefault(attacker, 0)
                    + plugin.getConfig().getInt("detection.killaura.dummy-hit-vl", 1);
            vlMap.put(attacker, vl);

            int limit = plugin.getConfig().getInt("detection.killaura.vl-limit", 5);
            String alert = "§d[Neko反作弊] §f玩家 §c" + attacker.getName()
                    + " §f疑似 §6KillAura（锁定+稳定角度+固定节奏）§f，目前 §dVL §f= §c" + vl + " §7/ §a" + limit;

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
        attackHistory.remove(name);
        lastTargetMap.remove(name);
        lastYawMap.remove(name);
        lastIntervalMap.remove(name);
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

    public boolean handleDummyCommand(CommandSender sender, String[] args) {
        sender.sendMessage("§c该指令已废弃，杀戮光环检测已切换为无NPC多因素模式");
        return true;
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
