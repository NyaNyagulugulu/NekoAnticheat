# 🐾 NekoAnticheat

🌸 **NekoAnticheat** 是一个为 Arclight / Paper / Spigot 生存服务器设计的轻量级高性能反作弊插件，旨在保护你的喵之国度不受非法行为干扰！

> 版本: `1.0.0`  
> 支持平台: Minecraft `1.21.1+`  
> 作者: 不穿胖次的小奶猫 (`1178878128`)  

---

## ✨ 功能特色

- ✅ **飞行检测**：精准判断玩家是否在非法飞行状态中。
- ✅ **KillAura 检测**：拦截自动攻击行为，保护服务器公平性。
- ✅ **假人检测**：通过行为模式分析识别可疑假人。
- ✅ **VL 系统**：内置违规等级系统，根据违规次数执行惩罚。
- ✅ **可配置性强**：支持 YAML 配置，灵活调整每项检测参数。
- ✅ **兼容性强**：与大部分主流插件良好兼容（Citizens、AuthMe、Geyser 等）。

---

## 🛠️ 安装方法

1. 下载最新的 `NekoAnticheat-x.x.x.jar` 插件文件。
2. 将插件放入服务器目录的 `plugins/` 文件夹中。
3. 启动服务器，会自动生成配置文件。
4. 编辑 `plugins/NekoAnticheat/config.yml` 调整参数（可选）。

---

## ⚙️ 配置说明

```yaml
debug: false

whitelist:
  blocks:
    - minecraft:slime_block
    - minecraft:scaffolding
  items:
    - minecraft:elytra
    - minecraft:trident

detection:
  fly:
    enabled: true
    vl-limit: 10
    vl: 2
    command: "kick {player} §c你正在飞喵~请不要作弊哦！"

  killaura:
    enabled: true
    vl-limit: 5
    vl: 1
    command: "ban {player} §c自动攻击行为被发现，喵呜~再见了"
