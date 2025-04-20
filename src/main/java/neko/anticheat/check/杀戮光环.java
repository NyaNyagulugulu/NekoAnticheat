package neko.anticheat.check;

import neko.anticheat.Anticheat;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.bukkit.Bukkit.getServer;

public class 杀戮光环 implements Listener {

    // 修复操你妈的空指针
    //Failed to handle packet net.minecraft.class_2828$class_2830@79ed87d4, suppressing error java.util.ConcurrentModificationException: null
    private final Anticheat plugin;
    private final ConcurrentHashMap<Player, NPC> npcMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Player, Integer> vlMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Player, Long> lastAttackMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Player, BukkitRunnable> followTasks = new ConcurrentHashMap<>();

    public 杀戮光环(Anticheat plugin) {
        this.plugin = plugin;
        startTimeoutTask();
        startVLReduceTask();
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        lastAttackMap.put(attacker, System.currentTimeMillis());

        if (!plugin.getConfig().getBoolean("detection.killaura.enabled")) return;

        if (CitizensAPI.getNPCRegistry().isNPC(event.getEntity())) {
            NPC npc = CitizensAPI.getNPCRegistry().getNPC(event.getEntity());
            if (npcMap.containsKey(attacker) && npcMap.get(attacker).getId() == npc.getId()) {
                int vl = vlMap.getOrDefault(attacker, 0)
                        + plugin.getConfig().getInt("detection.killaura.dummy-hit-vl", 1);
                vlMap.put(attacker, vl);

                int limit = plugin.getConfig().getInt("detection.killaura.vl-limit", 5);
                String alert = "§d[Neko反作弊] §f玩家 §c" + attacker.getName()
                        + " §f触发 §6杀戮光环检测§f，目前 §dVL §f= §c" + vl + " §7/ §a" + limit;

                sendColoredMessage(new String[]{alert});
                for (Player admin : Bukkit.getOnlinePlayers()) {
                    if (admin.hasPermission("nac.admin")) {
                        admin.sendMessage(alert);
                    }
                }

                if (vl >= limit) {
                    vlMap.remove(attacker);
                    removeNpc(attacker);
                    String cmd = plugin.getConfig().getString("detection.killaura.command",
                            "kick {player} 检测到杀戮光环！");
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", attacker.getName()));
                }
            }
        }

        if (!npcMap.containsKey(attacker)) {
            String name = (event.getEntity() instanceof Player p) ? p.getName() : randomName(5, 10);
            spawnBackNpc(attacker, name);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeNpc(event.getPlayer());
        vlMap.remove(event.getPlayer());
        lastAttackMap.remove(event.getPlayer());
    }

    public void spawnBackNpc(Player player, String npcName) {
        if (npcMap.containsKey(player)) return;

        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, npcName);
        npc.setProtected(true);
        npc.data().setPersistent("nameplate-visible", false); // 隐藏名字
        npcMap.put(player, npc);

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    removeNpc(player);
                    cancel();
                    return;
                }

                Location base = player.getLocation().clone();
                Vector back = base.getDirection().normalize().multiply(-2.0);
                Location spawnLoc = base.add(back);
                spawnLoc.setY(base.getY());
                spawnLoc.setDirection(player.getLocation().toVector().subtract(spawnLoc.toVector()));

                if (!spawnLoc.getChunk().isLoaded()) return;

                if (!npc.isSpawned()) {
                    try {
                        npc.spawn(spawnLoc);
                        if (npc.getEntity() instanceof Player npcEntity) {
                            npcEntity.setInvisible(true); // ✅ 让NPC实体完全隐身
                            for (Player online : Bukkit.getOnlinePlayers()) {
                                if (!online.equals(player)) {
                                    online.hidePlayer(plugin, npcEntity);
                                } else {
                                    online.showPlayer(plugin, npcEntity);
                                }
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("无法生成杀戮光环 NPC: " + e.getMessage());
                    }
                } else {
                    npc.getEntity().teleport(spawnLoc);
                }
            }
        };
        task.runTaskTimer(plugin, 0L, 1L);
        followTasks.put(player, task);
    }

    public void spawnNpcRing(Player player) {
        String name = randomName(5, 10);
        spawnBackNpc(player, name);
    }

    private void startTimeoutTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (Player p : new HashSet<>(npcMap.keySet())) {
                    long last = lastAttackMap.getOrDefault(p, 0L);
                    if (now - last > 10_000) {
                        removeNpc(p);
                        lastAttackMap.remove(p);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
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

    public void removeNpc(Player player) {
        NPC npc = npcMap.remove(player);
        if (npc != null) {
            if (npc.isSpawned()) npc.despawn();
            CitizensAPI.getNPCRegistry().deregister(npc);
        }
        BukkitRunnable task = followTasks.remove(player);
        if (task != null) task.cancel();
    }

    private String randomName(int min, int max) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        int len = min + new Random().nextInt(max - min + 1);
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < len; i++) {
            name.append(chars.charAt(new Random().nextInt(chars.length())));
        }
        return name.toString();
    }

    public boolean handleDummyCommand(CommandSender sender, String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("dummy")) {
            if (!sender.hasPermission("nac.admin")) {
                sender.sendMessage("§c你没有权限！");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("§c找不到该玩家！");
                return true;
            }

            spawnNpcRing(target);
            sender.sendMessage("§a已为 §e" + target.getName() + " §a生成杀戮光环假人！");
            return true;
        }

        return false;
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
