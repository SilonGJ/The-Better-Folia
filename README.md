> 由于此项目较潦草，bug也有点多，以及某些需要，所以暂时归档该项目  
> 需要类似协议支持可以前往 Lophine 的仓库，他们的实现兼容性更广，且 Bug 更少：
> https://github.com/LophineCraft/Lophine

# The-Better-Folia

一个高性能、兼容 Folia 的 Bukkit/Paper 服务端插件，提供 AppleSkin、Litematica EasyPlace 和 ServUX 协议支持，以及物品清理系统。

## 功能

### 协议支持
- **AppleSkin** — 向 AppleSkin 模组客户端同步玩家饱和度和疲劳度数据
- **Litematica EasyPlace** — 基于 ProtocolLib 的投影方块状态修正，配合 EasyPlace 模式使用
- **ServUX / MiniHUD 集成**
  - 实体数据预览（玩家、生物、方块实体）
  - HUD 数据（TPS、MSPT、游戏时间）
  - 容器预览（背包、末影箱）
  - 结构边界框显示
  - Litematica 原理图粘贴

### 物品清理
- 自动定时清理掉落物
- 可配置的清理前警告广播
- 公共垃圾桶 GUI（`/rubish`），支持分页
- 玩家投票系统（`/clean`）手动触发清理
- 可配置的垃圾桶自动清空

## 运行环境

- Java 21+
- Paper 1.21.x 或 Folia
- ProtocolLib

## 构建

```bash
./gradlew build -Pv=<版本号>
```

## 安装

1. 将 JAR 放入 `plugins/` 目录
2. 确保已安装 ProtocolLib
3. 重启或重载服务端
4. 根据需要编辑 `plugins/The-Better-Folia/config.yml`

## 命令

| 命令 | 权限 | 描述 |
|------|------|------|
| `/rubish` | `thebetterfolia.rubish` | 打开公共垃圾桶 |
| `/clean` | `thebetterfolia.clean` | 发起清理投票 |
| `/clean yes` | `thebetterfolia.clean` | 同意当前清理投票 |

## 权限

| 权限节点 | 默认 | 描述 |
|---------|------|------|
| `thebetterfolia.admin` | `op` | 所有管理命令 |
| `thebetterfolia.rubish` | `true` | 使用垃圾桶 |
| `thebetterfolia.clean` | `true` | 发起和参与清理投票 |
| `thebetterfolia.protocol.*` | `true` | 所有协议功能 |
| `thebetterfolia.protocol.appleskin` | `true` | AppleSkin 协议 |
| `thebetterfolia.protocol.litematica` | `true` | Litematica EasyPlace 协议 |
| `thebetterfolia.protocol.servux` | `true` | ServUX 协议 |

## 配置说明

完整配置详见 `config.yml`。主要模块：

- **`cleanup`** — 清理间隔、警告、垃圾桶、投票设置
- **`servux`** — 实体同步、HUD 同步、结构显示、容器预览开关
- **`schematica`** — Litematics 原理图和 EasyPlace 开关
- **`appleskin`** — AppleSkin 协议开关

## 关于项目

该项目最初服务于一个 Folia 服务器，因维护精力有限，现将其开源。
所以你可以看到多个毫不相关的功能集合于此。

## 许可

GPL 3.0
