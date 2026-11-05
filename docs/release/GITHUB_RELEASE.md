# OneKey v1.1.2 Release Notes (Minecraft 26.3 · Java 25)

🛠️ **OneKey v1.1.2「连锁破坏多人一致性与健壮性」维护版发布！**
本次修复了 1.1.x 里三个会实际影响玩家的缺陷（多人服开关不生效、异常路径丢物品、快捷栏保护可绕过），并把协议版本号与实际包结构对齐。

面向 Minecraft 26.3 的现代化、极轻量、零第三方运行时依赖操作优化模组，原生支持 Fabric 与 NeoForge 双加载器。

---

## 📦 发布产物下载 (Downloads)

| 加载器 (Loader) | Minecraft 版本 | 文件名 | 体积 |
|---|---|---|---|
| **Fabric** | 26.3 (Java 25) | `onekey-fabric-1.1.2.jar` | ~168 KB |
| **NeoForge** | 26.3 (Java 25) | `onekey-neoforge-1.1.2.jar` | ~167 KB |

> ⚠️ **从 1.1.2 起请保持双端同版本**：网络协议号已升到 `3`，与 1.0.x / 1.1.0 / 1.1.1 混连会被版本协商拒绝或静默丢包。

---

## 🐛 v1.1.2 修复摘要

1. **多人服里关掉「连锁破坏」仍然触发**：状态包新增玩家个人偏好（`optionsMask` / `maxBlocks`），服务端与服主配置逐项取交集 —— 布尔开关只能收紧不能放开，破坏上限取较小值并夹在 1..128。
2. **连锁破坏掉落聚合在异常路径可能永久丢物品**：掉落回吐与经验结算移入 `finally`，且**先关闭聚合开关再回吐**（否则回吐的实体又被 mixin 吞回去），合并失败时退化为把原始掉落逐叠散落。
3. **滚轮手势的快捷栏保护可被改过的客户端绕过**：服务端改为 `payload.includeHotbar() && config().gestures.includeHotbar`。
4. **协议版本号与实际包结构脱节**：`ProtocolVersion.CURRENT` 由 2 升到 3，javadoc 记录 1→2→3 的演进。

5. **隧道模式垂直挖掘方向错乱**：服务端此前用只看 yaw 的 `getDirection()` 冒充命中面，对着脚下/头顶挖时预览画竖井、服务端横挖；改为「眼睛 → 方块中心」经 `Direction.getApproximateNearest(...)` 重建命中面，并按 `eye.y - pos.getY() > 0.5` 计算上半格。
6. **NeoForge 端连锁时机早于真正破坏**：`BreakBlockEvent` 在破坏前触发，原点被领地拦下时连锁等于白送；改为入队 + 下一 tick 复核原点已成空气才执行。
7. **零散一致性**：NeoForge 连锁状态包补通道守卫（否则未装模组的服务器会把玩家踢下线）；HUD 破坏上限与 `clampMaxBlocks` 对齐；不可破坏方块不再预览为可连锁；断线重置按压状态；掉落聚合 mixin 改为失败可降级（`required: false`）。

## 🧹 其它改进

- 「每方块饥饿消耗」（`excavator.exhaustionPerBlock`，默认 0.005）真正生效：按本次实际破坏方块数计入疲劳，连锁不再是零代价。
- 删除从未生效的死配置：`compat.enabled` / `compat.clickDelayMs`（纯客户端点击降级占位）与 `excavator.showPreviewOutline`（从未实现的预览描边），语言文件同步清理为 93 键。
- 连锁状态包 20ms 完全重复去重；登录后与关闭设置界面/改配置后主动重发状态，避免服务端用默认值多跑一轮。
- 连锁破坏页签的总开关气泡补充说明：多人服务器中只能收紧服主配置；创造模式与旁观模式不触发。

## 🧪 测试与构建

- 新增 `ExcavatorOptionsTest`（6 例）：掩码往返、旧掩码回落、玩家只能收紧、上限夹取、边界与默认值。
- 合计 **136 例单测全绿**（Fabric 68 + NeoForge 68），`clean build` 无错误。
- 使用 Java 25 构建，运行于 Minecraft 26.3。

---

## 📥 安装方式

1. 安装对应加载器（Fabric Loader ≥ 0.20.1 + Fabric API 0.165.2+26.3；或 NeoForge 26.3.0.101）；
2. 将对应 jar 放入 `mods/` 目录；
3. 单人游戏可只装客户端；多人服建议**服务端与客户端都装**，否则整理/堆叠/连锁破坏等操作不会有服务端配合（客户端会提示「服务器未安装 OneKey」）。

## ⌨️ 默认按键

| 按键 | 功能 |
|---|---|
| `R` | 一键整理（先合并同类堆叠，再按分类 → 名称 → 数量排序） |
| `V` | 快速堆叠（并入附近容器中同类堆叠） |
| `B` | 一键存入（准星看哪存哪，智能防污染） |
| `K` | 设置界面（4 个分页） |
| `Alt + 左键` | 锁定 / 解锁槽位 |
| `~`（按住） | 连锁破坏；按住 `~` + 滚轮切换「同类连通 / 1x2 矿道 / 3x3 隧道」 |

## 🔧 已知限制

- 槽位锁定按容器类型记录（如 `minecraft:generic_9x3`），同尺寸容器共享锁定槽位；
- 快速堆叠受原版交互距离限制（约 8.5 格），配置里填写更大半径不会生效；
- 快速堆叠暂只豁免背包内锁定槽，外部容器内被锁定的槽位尚未跳过（整理已豁免）；
- 末影箱需打开界面才会参与；
- 整理与滚轮只在纯存储容器生效（箱子/木桶/末影箱/发射器/投掷器/漏斗/潜影盒）。

---

## 🔐 SHA-256

```
ed128012d6d0729a4034ef50e6fe9495928644e2e4a7cdb74c4f0936ef5d5e5a  onekey-fabric-1.1.2.jar
8a3e57947ead38d465bcf21f4caeb8cd14909483a5fdd359b8a1c579f47b1cf1  onekey-fabric-1.1.2-sources.jar
f936b4ae8bf7c9d77ec512dddb8cdcb29403c4cfe12f8438eedf630613cbde4f  onekey-neoforge-1.1.2.jar
23c0c4cd6276454eae66dd4c0675332a441eebc83b06f0ba18bd87ddd6c61a6b  onekey-neoforge-1.1.2-sources.jar
```

**许可证**：MIT · **源码**：[github.com/Roland114514/OneKey](https://github.com/Roland114514/OneKey)
