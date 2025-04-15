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
# 反作弊插件配置文件

# 调试模式
debug: false

# 白名单物品，针对非原版装备和方块
whitelist:
  # 方块
  blocks:
    - techreborn:launchpad #科技复兴“发射台”
  # 物品或者装备
  items:
    - techreborn:quantum_chestplate #科技复兴“量子胸甲”

detection:
  #飞行检测
  fly:
    enabled: true
    vl-limit: 25 # 最大vl值。vl值到达这个数值后执行下方指令
    vl: 30 # 每隔多少秒降低1vl值
    command: "ban {player} 检测到恶意飞行行为！ 3d"
  # 杀戮光环
  killaura:
    enabled: true
    vl-limit: 5
    vl: 5
    command: "ban {player} 检测到杀戮光环行为！ 3d"
    spawn-protect-radius: 16  # 出生点一定距离内不执行杀戮检测，防止空指针(单位区块)