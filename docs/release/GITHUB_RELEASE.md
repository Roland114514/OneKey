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
- **创造模式原生适配**：全面解除创造模式下对 R 键整理的硬编码拦截，在创造模式「生存模式物品栏」页签（`isInventoryOpen()`）或任意标签页下按 R 键均可秒速整理玩家物品栏。
- **虚拟目录保护机制**：非生存标签页下自动定向仅整理玩家实际背包与快捷栏数据（`player.inventoryMenu`），完全隔离虚拟物品选择目录（`CustomCreativeSlot`），杜绝物品目录被覆盖污染。
- **安全过滤保障**：依托既有的 `PlayerSlotFilter` 严密防护，创造模式装备槽（盔甲/副手）与垃圾桶槽位严格物理隔离，绝不发生误穿装备或误删物品。

### 2. 背包与容器界面右上角原版风格整理按键 (`showSortButton`)
- **原版风格 UI 挂载**：在生存背包（`InventoryScreen`）、创造背包（`CreativeModeInventoryScreen`）以及所有支持 R 键整理的容器（箱子、大箱子、木桶、潜影盒、末影箱、发射器/投掷器、漏斗等）GUI 右上角内侧安全区（`leftPos + imageWidth - 18, topPos + 5`）自动挂载原版材质 `12x12` 紧凑型快捷整理按键。
- **智能交互设计**：
  - 常规点击：点击整理当前容器（背包界面整理背包，容器界面整理容器本体）；
  - Shift + 点击：在任何容器界面下快速强制整理玩家背包；
  - 悬停 Tooltip 提示：原生展示 `一键整理 (R)` 与 `Shift + 点击：整理玩家背包` 引导。
- **配置与设置解耦**：在 `OneKeyConfig.sort.showSortButton` 新增布尔开关（默认开启），并在设置界面 Tab 0（一键整理）第 4 行右侧加入可视化切换按钮与中英文 Tooltip，支持自由隐藏。

---

## ⌨️ 默认键位速查

| 功能 | 默认热键 | 触发位置 |
|---|---|---|
| **一键整理** | `R` 或 `GUI右上角按钮` | 背包、创造背包、箱子、木桶、潜影盒等所有存储容器内 |
| **快速堆叠** | `V` | 准星对准容器（看哪存哪），或直接按键范围存入 |
| **一键存入** | `B` | 将非锁定物品全部存入周围容器（带智能分类防杂物保护） |
| **打开设置菜单** | `K` | 随时随地调出全功能设置界面 |
| **槽位锁定切换** | `左 Alt + 鼠标左键` | 悬停在任意槽位上加锁 / 解锁（支持锁定空槽） |
| **连锁破坏模式切换** | `~ + 鼠标滚轮` | 按住 `~` 键同时滚动滚轮在「连通/1x2/3x3」间切换 |

---

## 🔐 SHA-256

```
845aa61cbea42b4f14bce82e9987ccd502b6404f0f9d78a9005d241baf2b90fe  onekey-fabric-1.1.5.jar
ffd60e1ba14c15b4ff0237dbbd1e2abfedde27d4051730177d6a691e2c4ba307  onekey-fabric-1.1.5-sources.jar
2f870b1e66716b28dc6c5efa644bbfb9badef5faa1c86f59d9e9373a0ffc25be  onekey-neoforge-1.1.5.jar
fb5466f1031033c9d7f61e94e72fa1248b43752ed775843537df7f850ca7fa1d  onekey-neoforge-1.1.5-sources.jar
```

**许可证**：MIT · **源码**：[github.com/Roland114514/OneKey](https://github.com/Roland114514/OneKey)
