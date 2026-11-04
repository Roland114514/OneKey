# OneKey v1.0.1 Release Notes (Minecraft 26.3 · Java 25)

🛡️ **OneKey v1.0.1 稳定性维护版发布！**
面向 Minecraft 26.3 的现代化、极轻量、零第三方运行时依赖操作优化模组，原生支持 Fabric 与 NeoForge 双加载器。

---

## 📦 发布产物下载 (Downloads)

| 加载器 (Loader) | Minecraft 版本 | 文件名 | 体积 |
|---|---|---|---|
| **Fabric** | 26.3 (Java 25) | `onekey-fabric-1.0.1.jar` | ~101 KB |
| **NeoForge** | 26.3 (Java 25) | `onekey-neoforge-1.0.1.jar` | ~100 KB |

---

## 🛡️ v1.0.1 修复摘要

本次为健壮性与一致性问题修复版，**不改变任何交互与配置格式，可直接覆盖安装**：

- **配置损坏不再导致启动失败**：Fabric 端解析失败回退默认值并继续启动，损坏文件自动留档为 `config/onekey.json.corrupt`；写盘改为临时文件 + 原子替换。
- **`hotkeys` 配置真正生效**：`config/onekey.json` 中四个动作的默认键位（`key.keyboard.*`）被读取，非法键名回退内置默认并告警。
- **NeoForge 端友好降级**：服务端未安装 OneKey 时给出明确提示，未协商通道时不再发送无意义的整理/堆叠/锁定/滚轮数据包。
- **服务端关闭整理时界面一致**：主动回发权威容器快照 + 失败回执，消除「客户端已乐观排序、服务端没动」的界面错位。
- **快速堆叠半径硬上限**：服务端取客户端请求与服务端配置的较小值（1~32 格），改包无法绕过服务器设定。
- **Fabric 锁定状态同步补全**：写入被上限拒绝时也回发权威锁定快照，避免本地高亮与服务端长期不一致。
- **黑白名单语义拆分**：`whitelist`/`blacklist` 只匹配容器方块，新增 `itemWhitelist`/`itemBlacklist` 匹配物品（修复旧版白名单填物品时功能静默失效）。
- **滚轮微调服务端限流**：按玩家 40ms 最小间隔丢弃超频包，防止改包放大整容器广播。
- **整理失败可见提示**：服务端关闭整理时提示 `onekey.sort.cannot_sort`，不再「按了没反应」。
- **NeoForge 配置损坏同样留档**、`/reload` 刷新分类缓存、协议版本号统一取 `ProtocolVersion.CURRENT`，槽位锁定越界校验改为按当前菜单真实槽位范围。
- **修复背包/创造物品栏必崩（P0）**：26.3 的 `AbstractContainerMenu.getType()` 在 `menuType == null`（玩家背包 `InventoryMenu`、创造物品栏选择菜单）时直接抛 `UnsupportedOperationException`。此前在背包里滚动滚轮、创造物品栏按 R/Alt+左键/滚轮都会崩溃，服务端收到这类包也会抛未捕获异常（可被远程打崩）。现统一走 `menuTypeOrNull()` 安全降级，并给三类服务端处理器加了 `try/catch(Throwable)` 异常隔离。
- **测试**：公共内核单测 29 → 36，双加载器各跑一遍合计 **72 例全部通过**。

---

## ✨ 核心特性

- ⚡ **一键整理 (`R`)**：按「分类 → 名称 → 数量」排序，空槽后置；
- 🚀 **0ms 视觉响应**：本地射线置换瞬时反馈，服务端权威无缝校准；
- 🛡️ **快捷栏严格保护**：底部 0..8 快捷栏严格不参与背包整理与快速堆叠；
- 🔒 **槽位锁定 (`Alt + 左键`)**：琥珀色描边高亮保护，世界存档独立记忆；
- 📦 **附近容器快速堆叠与存入 (`V` / `B`)**：自动扫描周围 6 格容器，严格物品守恒；
- 🖱️ **鼠标手势增强**：Shift 双击批量同类转移、滚轮单件进出微调；
- ⚙️ **原生零依赖设置菜单 (`K`)**：无须第三方 UI 库，可视化调节所有参数；
- 🔍 **智能冲突检测**：自动检测 IPN、Mouse Tweaks、Item Scroller 等并提示。

---

## 🛠️ 校验和 (SHA-256 Checksums)

```text
5db68fa18af2afdac1afc72a308dc97c8e4be2de92f5fef6776d1e1a96e5b7f4  onekey-fabric-1.0.1.jar
a29766700d7dfadae988b6e203a14f79b9b594a7fb63d1b82ee295fd17ac7e81  onekey-neoforge-1.0.1.jar
```
