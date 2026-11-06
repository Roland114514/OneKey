# OneKey v1.1.5 Release Notes (Minecraft 26.3 · Java 25)

✨ **OneKey v1.1.5「创造模式背包整理与GUI原版快捷按键」版本发布！**

面向 Minecraft 26.3 的现代化、极轻量、零第三方运行时依赖操作优化模组，原生支持 Fabric 与 NeoForge 双加载器。
本次 v1.1.5 重点增强了一键整理（Sorting）的交互便利性与创造模式原生体验：全面放行创造模式背包 R 键一键整理，并在所有可整理的容器与背包界面右上角挂载原版风格的快捷整理物理按键。

---

## 📦 发布产物下载 (Downloads)

| 加载器 (Loader) | Minecraft 版本 | 文件名 | 体积 |
|---|---|---|---|
| **Fabric** | 26.3 (Java 25) | `onekey-fabric-1.1.5.jar` | ~175 KB |
| **NeoForge** | 26.3 (Java 25) | `onekey-neoforge-1.1.5.jar` | ~175 KB |

> ⚠️ **网络协议说明**：网络协议版本保持为 `3`，1.1.x 系列均保持良好兼容，强烈推荐双端统一更新至 1.1.5 享受最佳体验。

---

## ✨ v1.1.5 核心功能与体验优化

### 1. 创造模式背包 R 键一键整理生效
- **创造模式原生适配**：全面解除创造模式下对 R 键整理的硬编码拦截，在创造模式「生存模式物品栏」页签（`isInventoryOpen()`）下按 R 键即可秒速整理玩家物品栏。
- **虚拟目录安全防护**：非生存标签页下自动屏蔽按键并拦截 R 键整理，完全隔离虚拟物品选择目录（`CustomCreativeSlot`），杜绝物品目录被覆盖污染与虚拟快捷栏槽位脱节。
- **安全过滤保障**：依托既有的 `PlayerSlotFilter` 严密防护，创造模式装备槽（盔甲/副手）与垃圾桶槽位严格物理隔离，绝不发生误穿装备或误删物品。

### 2. 背包物品栏上方原版风格整理按键 (`showSortButton`)
- **UI 呈现与位置优化**：按键文本采用清晰的原版风格字母 **"R"**（呼应 R 键热键），位置从页面最顶部下移至**背包物品栏正上方**（`inventoryLabelY - 2`，对齐第 9 列物品槽），避免顶着整个页面的右上方，与原版 GUI 排版完美贴合。
- **原版风格 UI 挂载**：在生存背包（`InventoryScreen`）、创造背包（`CreativeModeInventoryScreen`）以及所有支持 R 键整理的容器（箱子、大箱子、木桶、潜影盒、末影箱、发射器/投掷器、漏斗等）GUI 物品栏上方区域自动挂载原版材质 `12x12` 紧凑型快捷整理按键。
- **动态坐标与可见性帧前同步**：
  - 自适应配方书：在玩家打开/关闭配方书导致 `leftPos` 产生 77px 动态偏移时，每帧渲染与鼠标命中前自动同步绝对坐标，按键始终精准吸附在背包物品栏上方；
  - 创造模式标签页安全感知：创造模式非生存物品栏标签页下自动隐藏并禁用按键，生存模式页签下恢复可见与交互。
- **智能交互设计**：
  - 常规点击：点击整理当前容器（背包界面整理背包，容器界面整理容器本体）；
  - Shift + 点击：在任何容器界面下快速强制整理玩家背包；
  - 悬停 Tooltip 提示：原生展示 `一键整理 (R)` 与 `Shift + 点击：整理玩家背包` 引导。
- **配置与设置解耦**：在 `OneKeyConfig.sort.showSortButton` 新增布尔开关（默认开启），并在设置界面 Tab 0（一键整理）第 4 行右侧加入可视化切换按钮与中英文 Tooltip，支持自由隐藏。

### 3. 创造模式背包鼠标手势完整支持与安全防护
- **全手势生态解锁**：解除此前针对 `CreativeModeInventoryScreen` 的一刀切拦截，全面支持槽位锁定、Shift+左键拖拽扫掠、右键划动重复放置、滚轮微调单件放置、Shift+双击同类批量移动。
- **双页签精细隔离**：
  - 生存模式物品栏页签（`isInventoryOpen() == true`）：放行主背包与快捷栏之间的扫掠、同类归集与滚轮持物放入；
  - 分类/搜索页签（`isInventoryOpen() == false`）：全面阻断 Shift+双击与 Shift+扫掠（防止误清空快捷栏及抢占滚动条），100% 放行目录翻页滚轮；
  - 底部快捷栏在任一页签下均支持槽位锁定与右键划线投放。
- **销毁槽与无限物品槽绝对免疫**：严格使用 `slot.container == player.getInventory()` 与 `PlayerSlotFilter` 进行多层防御，销毁物品槽（`destroyItemSlot`）与 45 个创造无限槽受到底层硬隔离保护，绝无误删或幽灵物品。

---

## 🛡️ 4-argon 审查与缺陷修复

本次发布经由 `gemini-4-argon` 对 26.3 GUI 生命周期及创造模式容器状态机进行了对抗性审查（评分 98/100 PASS），闭环修复与验证了 4 项核心边界缺陷：
1. **配方书动态位移同步**：在 Fabric（`ScreenEvents.beforeExtract`）与 NeoForge（`ScreenEvent.Render.Pre`）双端注入帧前坐标更新，同时在 `SortButton.isMouseOver` 及 `extractContents` 中双重同步，使按钮时刻吸附于物品栏上方；
2. **创造模式目录页按键重叠规避**：在分类及搜索页签下强制隐藏按键，防止遮挡搜索条与滚动条；
3. **创造模式非生存页签按键阻断**：在 `triggerScreenSort` 与 `triggerButtonSort` 中严格限定只在 `creative.isInventoryOpen()` 时触发，杜绝虚拟槽位与真实背包的数据冲突；
4. **创造模式手势边界与状态机污染隔离**：点击非玩家槽位（如切换顶部页签、点击搜索框等）时主动执行 `resetGestureState()` 清空拖拽轨迹状态机；双端 `slotClicked` 均通过 Accessor 调用 `CreativeModeInventoryScreen.slotClicked`，保证 `SlotWrapper` 解包与发包同步正常。

---

## ⌨️ 默认键位速查

| 功能 | 默认热键 | 触发位置 |
|---|---|---|
| **一键整理** | `R` 或 `物品栏上方R按键` | 背包、创造背包、箱子、木桶、潜影盒等所有存储容器内 |
| **快速堆叠** | `V` | 准星对准容器（看哪存哪），或直接按键范围存入 |
| **一键存入** | `B` | 将非锁定物品全部存入周围容器（带智能分类防杂物保护） |
| **打开设置菜单** | `K` | 随时随地调出全功能设置界面 |
| **槽位锁定切换** | `左 Alt + 鼠标左键` | 悬停在任意槽位上加锁 / 解锁（支持锁定空槽） |
| **连锁破坏模式切换** | `~ + 鼠标滚轮` | 按住 `~` 键同时滚动滚轮在「连通/1x2/3x3」间切换 |

---

## 🔐 SHA-256

```
cab5ced8a667b0d23adfcdd3239506e04e64d0ff2421a7a127abb28e4279f611  onekey-fabric-1.1.5.jar
517cc6a76d3facce18f915e7b16cd38b2133c00a3e8f5a4f5af1ace4acbc35f1  onekey-fabric-1.1.5-sources.jar
743ee7c8841c946bc61781d846f70943d7dfdc4e47383e7124a1f764b4b973f5  onekey-neoforge-1.1.5.jar
6ae6434eeb94ffa53874194d8d550ea742b71028cb92f869815afe4ec7a4dc98  onekey-neoforge-1.1.5-sources.jar
```

**许可证**：MIT · **源码**：[github.com/Roland114514/OneKey](https://github.com/Roland114514/OneKey)
