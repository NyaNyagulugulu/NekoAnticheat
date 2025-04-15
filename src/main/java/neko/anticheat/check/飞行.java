// 飞行检测模块 - 用于检测非法空中行为，包括原地漂浮、鞘翅绕过、船体上升等
package neko.anticheat.check;

import neko.anticheat.Anticheat;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.NamespacedKey;

import java.util.*;

import static org.bukkit.Bukkit.getServer;

public class 飞行 implements Listener {

    private final Anticheat plugin;

    // 玩家当前 VL（违规等级）
    private final Map<Player, Integer> vlMap = new HashMap<>();
    // 是否已提醒（防止重复通知）
    private final Set<Player> alerted = new HashSet<>();
    // 玩家浮空 tick 计数
    private final Map<Player, Integer> playerFloatTicks = new HashMap<>();
    // 船体浮空 tick 计数
    private final Map<Boat, Integer> boatFloatTicks = new HashMap<>();
    // 船体上一次 Y 坐标
    private final Map<Boat, Double> lastBoatY = new HashMap<>();
    // 船体上一次位置
    private final Map<Boat, Location> lastBoatLocation = new HashMap<>();
    // 玩家豁免时间（用于跳过检测）
    private final Map<Player, Long> suspendedPlayers = new HashMap<>();

    // 白名单方块与装备
    private Set<String> whitelistBlocks = new HashSet<>();
    private Set<String> whitelistItems = new HashSet<>();

    public 飞行(Anticheat plugin) {
        this.plugin = plugin;
        loadWhitelist(); // 读取白名单配置
        startReduceTask(); // 启动定时 VL 递减任务
    }

    // 读取配置文件中的白名单内容
    private void loadWhitelist() {
        whitelistBlocks = new HashSet<>(plugin.getConfig().getStringList("whitelist.blocks"));
        whitelistItems = new HashSet<>(plugin.getConfig().getStringList("whitelist.items"));
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // 基础排除条件
        if (!plugin.getConfig().getBoolean("detection.fly.enabled")) return;
        if (player.getGameMode() != GameMode.SURVIVAL) return;
        if (player.isFlying()) return;
        if (player.isGliding()) return;

        // 白名单方块判断（脚底下）
        Block under = player.getLocation().subtract(0, 1, 0).getBlock();
        NamespacedKey blockKey = under.getType().getKey();
        boolean isOnWhitelistBlock = whitelistBlocks.contains(blockKey.getNamespace() + ":" + blockKey.getKey());

        // 记录豁免状态（10秒）
        if (isOnWhitelistBlock) {
            suspendedPlayers.put(player, System.currentTimeMillis() + 10000);
        }

        // 检查是否处于豁免状态（方块或药水）
        boolean isTemporarilyExempt = suspendedPlayers.containsKey(player)
                && System.currentTimeMillis() < suspendedPlayers.get(player);
        if (isTemporarilyExempt) {
            if (plugin.getConfig().getBoolean("debug")) {
                plugin.getLogger().info("[飞行检测] 玩家 " + player.getName() + " 正在白名单方块触发的豁免状态中，跳过检测");
            }
            return;
        }

        // 白名单装备判断（护甲栏）
        boolean wearingWhitelistItem = false;
        for (ItemStack armorItem : player.getInventory().getArmorContents()) {
            if (armorItem != null && armorItem.getType() != Material.AIR) {
                NamespacedKey itemKey = armorItem.getType().getKey();
                String fullName = itemKey.getNamespace() + ":" + itemKey.getKey();
                if (whitelistItems.contains(fullName)) {
                    wearingWhitelistItem = true;
                    break;
                }
            }
        }

        if (plugin.getConfig().getBoolean("debug") && wearingWhitelistItem) {
            plugin.getLogger().info("[飞行检测] 玩家 " + player.getName() + " 穿戴了白名单装备，跳过检测");
        }
        if (wearingWhitelistItem) return;

        // 漂浮药水豁免
        if (player.hasPotionEffect(PotionEffectType.LEVITATION)) {
            suspendedPlayers.put(player, System.currentTimeMillis() + 10000);
            return;
        }
        if (suspendedPlayers.containsKey(player) && System.currentTimeMillis() < suspendedPlayers.get(player)) {
            return;
        } else {
            suspendedPlayers.remove(player);
        }

        // 地形与水体状态判断
        Location loc = player.getLocation();
        boolean onGround = player.isOnGround();
        boolean inWater = false;

        Location base = loc;
        Location mid = base.clone().add(0, 0.5, 0);
        Location head = base.clone().add(0, 1.5, 0);

        for (Location checkLoc : Arrays.asList(base, mid, head)) {
            Material type = checkLoc.getBlock().getType();
            if (type == Material.WATER || type == Material.BUBBLE_COLUMN || type == Material.KELP
                    || type == Material.SEAGRASS || type == Material.TALL_SEAGRASS) {
                inWater = true;
                break;
            }
        }
        inWater = inWater || player.isSwimming() || player.isInWater();

        double deltaY = event.getTo().getY() - event.getFrom().getY();

        // 玩家悬浮检测（未乘坐载具）
        if (!player.isInsideVehicle()) {
            Block currentBlock = player.getLocation().getBlock();
            Material currentType = currentBlock.getType();
            if (currentType == Material.LADDER || currentType.toString().contains("VINE")) return;

            if (!onGround && !inWater) {
                if (deltaY >= 0) {
                    int ticks = playerFloatTicks.getOrDefault(player, 0) + 1;
                    playerFloatTicks.put(player, ticks);

                    int jumpLevel = 0;
                    PotionEffect jumpEffect = player.getPotionEffect(PotionEffectType.JUMP_BOOST);
                    if (jumpEffect != null) {
                        jumpLevel = jumpEffect.getAmplifier() + 1;
                    }
                    int limitTick = 8 + jumpLevel * 2;

                    if (ticks >= limitTick) {
                        raiseVL(player, "空中停留异常（" + ticks + " ticks）");
                    }
                } else {
                    playerFloatTicks.remove(player);
                    alerted.remove(player);
                }
            } else {
                playerFloatTicks.remove(player);
                alerted.remove(player);
            }
            return;
        }

        // 船体异常检测
        Entity vehicle = player.getVehicle();
        if (vehicle != null && vehicle.getType() == EntityType.BOAT) {
            Boat boat = (Boat) vehicle;
            Location boatLoc = boat.getLocation();
            Block boatUnder = boatLoc.clone().subtract(0, 1, 0).getBlock();

            boolean boatOnAir = boatUnder.getType() == Material.AIR
                    || boatUnder.getType() == Material.CAVE_AIR
                    || boatUnder.getType() == Material.VOID_AIR;

            double currentY = boatLoc.getY();
            double lastY = lastBoatY.getOrDefault(boat, currentY);
            lastBoatY.put(boat, currentY);
            double yDiff = currentY - lastY;

            boolean isFalling = yDiff < -0.05;
            boolean isFloating = yDiff > -0.03 && yDiff < 0.03;

            Location lastLoc = lastBoatLocation.getOrDefault(boat, boatLoc);
            lastBoatLocation.put(boat, boatLoc.clone());
            double dx = boatLoc.getX() - lastLoc.getX();
            double dz = boatLoc.getZ() - lastLoc.getZ();
            double speed = Math.sqrt(dx * dx + dz * dz);
            boolean isMoving = speed > 0.05;

            boolean debug = plugin.getConfig().getBoolean("debug", false);
            if (debug) {
                plugin.getLogger().info("[BoatCheck] 玩家: " + player.getName()
                        + " | yDiff=" + String.format("%.4f", yDiff)
                        + " | 水平位移=" + String.format("%.3f", speed)
                        + " | 下方=" + boatUnder.getType());
            }

            if (boatOnAir && !isFalling) {
                boolean trigger = false;
                if ((isFloating && isMoving) || (yDiff > 0.05)) {
                    trigger = true;
                }

                if (trigger) {
                    int ticks = boatFloatTicks.getOrDefault(boat, 0) + 1;
                    boatFloatTicks.put(boat, ticks);

                    if (debug) {
                        plugin.getLogger().info("[BoatCheck] → 可疑行为计数：" + ticks + " ticks");
                    }

                    if (ticks >= 6) {
                        raiseVL(player, "船体空中异常移动（" + ticks + " ticks）");
                    }
                } else {
                    boatFloatTicks.remove(boat);
                }
            } else {
                boatFloatTicks.remove(boat);
            }
        }
    }

    // 累加 VL 并执行处罚命令
    private void raiseVL(Player player, String reason) {
        int oldVL = vlMap.getOrDefault(player, 0);
        int newVL = oldVL + 1;
        vlMap.put(player, newVL);

        int limit = plugin.getConfig().getInt("detection.fly.vl-limit", 5);
        String msg = "§f玩家 §c" + player.getName()
                + " §f触发 §6飞行检测§f（" + reason + "），§dVL §f= §c" + newVL + " §7/ §a" + limit;

        if (!alerted.contains(player)) {
            sendColoredMessage(new String[]{msg});
            for (Player admin : Bukkit.getOnlinePlayers()) {
                if (admin.hasPermission("nac.admin")) {
                    admin.sendMessage("§d[Neko反作弊] " + msg);
                }
            }
            alerted.add(player);
        }

        if (newVL >= limit) {
            vlMap.remove(player);
            alerted.remove(player);
            playerFloatTicks.remove(player);
            String raw = plugin.getConfig().getString("detection.fly.command",
                    "kick {player} 检测到飞行作弊");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), raw.replace("{player}", player.getName()));
        }
    }

    // 控制台发送彩色消息
    private void sendColoredMessage(String[] messages) {
        for (String m : messages) {
            getServer().getConsoleSender().sendMessage(
                    ChatColor.translateAlternateColorCodes('&',
                            "&8[&dNeko反作弊&8] &r" + m)
            );
        }
    }

    // 定时减少 VL 值
    private void startReduceTask() {
        int interval = plugin.getConfig().getInt("detection.fly.vl", 5);
        long ticks = interval * 20L;

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : new HashSet<>(vlMap.keySet())) {
                    int vl = vlMap.get(p);
                    if (vl > 0) vlMap.put(p, vl - 1);
                }
            }
        }.runTaskTimer(plugin, ticks, ticks);
    }

    // 获取玩家当前 VL
    public int getVL(Player player) {
        return vlMap.getOrDefault(player, 0);
    }
}