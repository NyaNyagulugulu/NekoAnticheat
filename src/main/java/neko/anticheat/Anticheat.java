package neko.anticheat;

import neko.anticheat.check.杀戮光环;
import neko.anticheat.check.飞行;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Objects;

public class Anticheat extends JavaPlugin {

    private static Anticheat instance;
    private 飞行 飞行检测;
    private 杀戮光环 杀戮检测;

    public static Anticheat getInstance() {
        return instance;
    }

    public 杀戮光环 get杀戮检测() {
        return 杀戮检测;
    }

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        重载配置 command = new 重载配置();
        Objects.requireNonNull(this.getCommand("nac")).setExecutor(command);
        Objects.requireNonNull(this.getCommand("nac")).setTabCompleter(command);

        this.飞行检测 = new 飞行(this);
        Bukkit.getPluginManager().registerEvents(飞行检测, this);

        this.杀戮检测 = new 杀戮光环(this);
        Bukkit.getPluginManager().registerEvents(杀戮检测, this);

        sendColoredMessage(new String[] {
                "§d   _   _           _              _         _       _      ",
                "§d  | \\ | | _____  _| |_ ___  _ __ | |_ ___  | |_ ___| |__   ",
                "§d  |  \\| |/ _ \\ \\/ / __/ _ \\| '_ \\| __/ _ \\ | __/ __| '_ \\  ",
                "§d  | |\\  |  __/>  <| || (_) | | | | || (_) || || (__| | | | ",
                "§d  |_| \\_|\\___/_/\\_\\\\__\\___/|_| |_|\\__\\___/  \\__\\___|_| |_| ",
                "§b   ____        _        _     _           _     _         ",
                "§b  |  _ \\ _   _| | _____| |__ (_)_ __ ___ | |__ | |_ _   _ ",
                "§b  | |_) | | | | |/ / _ \\ '_ \\| | '_ ` _ \\| '_ \\| __| | | |",
                "§b  |  __/| |_| |   <  __/ |_) | | | | | | | |_) | |_| |_| |",
                "§b  |_|    \\__,_|_|\\_\\___|_.__/|_|_| |_| |_|_.__/ \\__|\\__, |",
                "§b                                                    |___/ ",
                "§b⋆ &3版本 &f{version} &a| &3作者 &f不穿胖次の小奶猫",
                "§b⋆ &3QQ &f1178878128 &a| &3Telegram &ft.me/maomaoChengguang",
                "§d⚡ 反作弊已成功加载！喵呜～准备好开始冒险了吗？"
        });
    }

    @Override
    public void onDisable() {
        getLogger().info("Neko反作弊插件已关闭。");
    }

    public File getConfigFile() {
        return new File(getDataFolder(), "config.yml");
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
