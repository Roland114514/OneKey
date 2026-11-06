# OneKey v1.1.7 Release Notes (Minecraft 26.3 · Java 25)

✨ **OneKey v1.1.7「创造模式背包手势扫掠修复版」发布！**

面向 Minecraft 26.3 的现代化、极轻量、零第三方运行时依赖操作优化模组，原生支持 Fabric 与 NeoForge 双加载器。
本次 v1.1.7 重点修复了玩家在处于创造模式背包时，鼠标手势中的 `Shift + 左键` 快速扫掠（LMB Drag Sweep）失效的问题，经由 `gemini-4-argon` 对 26.3 `CreativeModeInventoryScreen` 与底层字节码机制的深度推导，实现解包判定与执行层同步的分离架构，在 100% 保持生存模式手势、创造模式双页签边界防护及服务端数据包同步的前提下彻底解决该问题。

---

## 📦 发布产物下载 (Downloads)

| 加载器 (Loader) | Minecraft 版本 | 文件名 | 体积 |
|---|---|---|---|
| **Fabric** | 26.3 (Java 25) | `onekey-fabric-1.1.7.jar` | ~180 KB |
| **NeoForge** | 26.3 (Java 25) | `onekey-neoforge-1.1.7.jar` | ~180 KB |

> ⚠️ **网络协议说明**：网络协议版本保持为 `3`，1.1.x 系列均保持良好兼容，强烈推荐双端统一更新至 1.1.7 享受最佳体验。

---

## 🐛 v1.1.7 核心缺陷修复与增强

### 1. `SlotWrapper` 底层解包机制与物理槽位索引纠正
- **安全递归解包**：在 `PlayerSlotFilter` 中提供反射缓存的静态 `unwrap(Slot slot)` 方法，对原版创造模式内部包装类 `CreativeModeInventoryScreen$SlotWrapper` 进行安全递归提取，直达底层真实的 `Slot` 实例，在无客户端环境（服务端）下优雅降级。
- **物理槽位索引纠正**：修复了创造模式生存页签快捷栏槽位被包装后容器槽位返回 `36..44` 导致被 `InventorySlots.isIncluded` 误判越界（`>= 36`）直接拒绝扫掠的问题，精准恢复快捷栏 `0..8` 与主背包 `9..35` 的手势扫掠支持。

### 2. 执行层与发包同步保护（杜绝幽灵物品）
- **判定与执行解耦**：在判定层（类型检查、加锁判定、有效性过滤）使用解包后的真实底层槽位；在执行层（向 `slotClicked` 传递）严格保留原始未解包的 `SlotWrapper`，满足原版 `CreativeModeInventoryScreen.slotClicked` 内部强转与发包监听器要求，彻底杜绝 `ClassCastException` 与断流幽灵物品隐患。
- **全背包误清空防护**：非生存页签（分类与搜索列表）及销毁物品槽（`destroyItemSlot`）严格拦截扫掠，彻底消除划过垃圾桶槽导致全背包被原版逻辑误清空的严重风险。

### 3. 原版 `QuickCraft` 拖拽状态机抢占彻底拦截
- **状态机冲突阻断**：扫掠触发时主动清空 `quickCraftSlots` 并重置 `isQuickCrafting = false`；Fabric 端返回 `false`、NeoForge 端调用 `event.setCanceled(true)`，杜绝原版拖拽分摊状态机抢占或与快速移动产生冲突。

### 4. 创造模式背包辅助手势与视觉高亮全面校准
- **滚轮微调与批量转移支持**：修复了创造模式生存页签下鼠标滚轮单件转移及 Shift+双击同类批量转移对快捷栏槽位失效的问题。
- **槽位加锁与边框高亮精准对应**：在创造模式生存页签下，Alt+左键加锁及琥珀色锁定边框、浮窗提示全面支持 0..35 格精确查询与渲染。

### 5. 单元测试强化
- 在 `fabric` 测试集中新增 `PlayerSlotFilterTest`，包含针对 `unwrap` 与 `SlotWrapper` 反射还原的单元测试用例，全套 149 项测试全绿通过。

---

## ⌨️ 默认键位速查

| 功能 | 默认热键 | 触发位置 |
|---|---|---|
| 一键整理 | `R` 或 `物品栏上方按键` | 背包、创造背包、箱子、木桶、潜影盒等所有支持容器 |
| 快速堆叠 | `V` | 准星对准容器（看哪存哪），或直接按距离并入同类 |
| 一键存入 | `B` | 将非锁定物品全部存入周围容器（带智能防污染） |
| 打开设置菜单 | `K` | 随时随地调出全功能设置界面 |
| 槽位锁定切换 | `左 Alt + 鼠标左键` | 悬停在任意槽位上加锁 / 解锁（支持原版自定义按键重绑） |
| 连锁破坏模式切换 | `~ + 鼠标滚轮` | 按住 `~` 键同时滚动滚轮在「连通/1x2/3x3」间快速轮换 |

---

## 🔐 SHA-256

```text
da68d0bcb9c0951d61ee7e90995997a1904a88f0776e9f2f7b2813823390687d  onekey-fabric-1.1.7.jar
7f293b81dc4afd4baf3d2815ba8b2562355f31cc61d2f6a515616b8c7df3f140  onekey-fabric-1.1.7-sources.jar
6956f22f48432c0d16e816e4e068156edd0be137231bcd27b9894a0cf7512757  onekey-neoforge-1.1.7.jar
09867c1c3248a19bdbccfcc59e5faf1860a568e3d6cb0166b9aeda41a8668960  onekey-neoforge-1.1.7-sources.jar
```

许可证：MIT · 源码：github.com/Roland114514/OneKey
