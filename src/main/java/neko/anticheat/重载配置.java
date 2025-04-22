package neko.anticheat;

import neko.anticheat.check.杀戮光环;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class 重载配置 implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            Anticheat.getInstance().reloadConfig();
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&8[&dNeko反作弊&8] &a配置已重载！喵～"));
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("dummy")) {
            if (!sender.hasPermission("nac.admin")) {
                sender.sendMessage(ChatColor.RED + "你没有权限使用该指令！");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "找不到该玩家！");
                return true;
            }

            杀戮光环 killaura = Anticheat.getInstance().get杀戮检测();
        }

        sender.sendMessage(ChatColor.RED + "用法: /nac reloa");
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1) {
            List<String> sub = new ArrayList<>();
            sub.add("reload");
            sub.add("dummy");
            return sub.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("dummy")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return names;
        }

        return Collections.emptyList();
    }
}
