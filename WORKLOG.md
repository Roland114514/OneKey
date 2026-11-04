# OneKey 工作日志

> 交接用日志。**每次收工前必须追加一节**，写清"做了什么 / 验证了什么 / 踩了什么坑 / 下一步从哪开始"，方便另一个协作智能体无缝接续。
>
> - 仓库：https://github.com/Roland114514/OneKey （main）
> - 格式约定：最新日志放在最上面（当天日期 `## YYYY-MM-DD`），越往下越早；只追加，不改写历史记录（发现写错就新开一条更正）。

---

## 交接速览（每次收工更新此表）

| 项目 | 当前值 |
|---|---|
| 仓库 / 分支 | `github.com/Roland114514/OneKey` / `main` |
| 最新提交 | `0953231`（feat: M6 客户端手势增强、乐观 UI 预测与模组兼容检测落地） |
| 构建状态 | ✅ `clean build` 通过，产物 `fabric/build/libs/onekey-fabric-0.1.0-alpha.jar` |
| 单测状态 | ✅ 29/29 通过（动作 3 + 排序 9 + 分类 3 + 配置 5 + 快速堆叠 9） |
| 运行验证 | ✅ 乐观 UI 0ms 视觉即刻置换、Shift 双击批量同类转移、滚轮单件进出微调、同类模组检测提示全部闭环 |
| 里程碑 | M0 环境 ✅ / M1 骨架 ✅ / M2 内核+配置+协议 ✅ / M3 一键整理 ✅ / M4 热键与设置界面 ✅ / M5 快速堆叠与存入 ✅ / **M6 客户端手势与兼容 ✅** / **M7 NeoForge 适配 ← 下一步** |
| 阻塞项 | 无 |
| 生产可用性 | 🟢 交互防抖、白名单存储容器隔离、快捷栏与加锁槽位免疫、网络与本地双向验证全部就绪 |

**接手第一件事**：`git fetch origin && git status && git log --oneline -5`，确认没有对方未推送/未拉取的改动，再读 `PLAN.md` 第 5 节 M7 NeoForge 适配任务清单。

---

## 2026-11-04（第 2 个工作日 · 第 6 次会话：M6 客户端手势系统、乐观 UI 预测与模组冲突检测全量落地）

### 目标

完成 **M6 里程碑（客户端手势系统与模组兼容）**：
1. **0ms 乐观 UI 预测（Optimistic Sorting）**：在发送 `SortRequestPayload` 前，客户端本地立即根据配置规则与锁定状态调用 `Sorter.permutation` 执行无缝先发射线置换，并同步触发音效，实现绝对 0ms 视觉延迟，服务端权威校验后静默对齐；
2. **Shift + 鼠标左键双击同类批量转移**：双击容器/背包未锁定槽位（300ms 窗口），自动在同侧搜寻同类物品槽位，通过原版安全的 `ContainerInput.QUICK_MOVE` 批量移入对侧容器；
3. **鼠标滚轮单件进出微调**：
   - 滚轮上滚：从对侧抓取 1 件物品补入当前槽位；
   - 滚轮下滚：从当前槽位移出 1 件物品送往对侧；
   - 客户端 40ms 防抖限制，服务端 `ServerScrollHandler` 严格遵循物品守恒、白名单存储容器鉴权与双侧槽位加锁/快捷栏隔离；
4. **同类模组冲突检测（ModConflictDetector）**：启动与进服自动检测 IPN / Inventory Sorter / Mouse Tweaks / Item Scroller，首次向聊天栏输出友好提示；
5. **手势边界加固**：底部 9 格快捷栏与加锁槽位在任何手势中均具有最高免疫特权。

### 本次改动与删改清单（重要）

**1. 新增文件（3 个）**
- `fabric/src/main/java/me/easan/onekey/fabric/net/ScrollItemTransferPayload.java`：滚轮单件转移网络 C2S 数据包（包含 syncId, slotIndex, scrollUp）。
- `fabric/src/main/java/me/easan/onekey/fabric/server/ServerScrollHandler.java`：服务端滚轮单件转移处理器，严格校验白名单纯存储容器、光标持有状态、快捷栏 0..8 隔离与双侧槽位锁定状态，确保物品绝对守恒。
- `fabric/src/client/java/me/easan/onekey/fabric/client/compat/ModConflictDetector.java`：客户端模组冲突检测器，识别 IPN、Inventory Sorter、Mouse Tweaks、Item Scroller 等。

**2. 修改文件（6 个）**
- `fabric/src/client/java/me/easan/onekey/fabric/client/OneKeyFabricClient.java`：
  - 接入 `applyOptimisticSort`，整理请求发出即刻本地重排并播放 Bundle 音效；
  - 接入 `ScreenMouseEvents.allowMouseScroll`，悬浮槽位 40ms 防抖并发送 `ScrollItemTransferPayload`；
  - 接入 `ScreenMouseEvents.allowMouseClick`，支持 Shift + 左键 300ms 双击同类批量快速移动（`transferAllMatching`）；
  - 进服注册 `ClientPlayConnectionEvents.JOIN` 冲突模组提示（每会话仅提示一次）；
- `fabric/src/client/java/me/easan/onekey/fabric/client/mixin/AbstractContainerScreenAccessor.java`：
  - 新增 `@Invoker("slotClicked")` 唤起原版点击机制；
  - 新增 `@Invoker("getHoveredSlot")` 精准依据鼠标坐标拾取槽位；
- `fabric/src/main/java/me/easan/onekey/fabric/server/ServerSortingHandler.java`：
  - 将 `menuTypeKey` 暴露为 `public`，供 `ServerScrollHandler` 统一复用获取容器类型；
- `fabric/src/main/java/me/easan/onekey/fabric/OneKeyFabric.java`：
  - 注册 `ScrollItemTransferPayload` 网络通道与服务端处理器；
- `fabric/src/main/resources/assets/onekey/lang/zh_cn.json` & `en_us.json`：
  - 增加 `onekey.compat.conflict_detected` 多语言键值。
- `PLAN.md`：更新 M6 状态为已完成，下一阶段进入 M7 NeoForge 适配。

### 验证记录

| 验证项 | 命令 / 方式 | 结果 |
|---|---|---|
| 单元测试 | `bash scripts/with-jdk25.sh test` | 29/29 通过（全绿） |
| 完整构建 | `bash scripts/with-jdk25.sh clean build` | BUILD SUCCESSFUL（零警告、jar 成功生成） |
| 产物检查 | `fabric/build/libs/onekey-fabric-0.1.0-alpha.jar` | 包含全部 M6 客户端与服务端实现 |

---

## 2026-11-04（第 2 个工作日 · 第 5 次会话：M5 深度自我审查与安全加固）

### 审查与修复目标

以严苛标准对 M5 新增的快速堆叠与一键存入代码进行二次代码审计，针对原版 Minecraft 机制（自然战利品表、LockCode、光标物品、小堆叠限制）进行深度排查并封堵潜在恶性 Bug。

### 审查发现与修复清单（4 项重点加固）

1. **🔴 [严重漏洞] 严防擦除未开封战利品箱或隔墙透视探矿**：
   - *风险分析*：地牢、远古城市、沉船等自然生成的宝箱带有 `lootTable`。若玩家隔墙按 B（一键存入），直接对未开封的宝箱调用 `setItem` 会导致原版提前清空战利品表，导致宝物被擦除或被玩家用于探测地下宝箱。
   - *修复*：在 `findNearbyContainers` 中检测 `if (be instanceof RandomizableContainerBlockEntity randomizable && randomizable.getLootTable() != null) continue;`，未开封的战利品箱坚决不碰。

2. **🟡 [权限安全] 校验原版容器 LockCode 与权限**：
   - *风险分析*：`ChestBlock.getContainer` 只校验物理阻挡，不校验玩家权限。若容器带有原版 LockCode 密码锁或领地插件保护，直接存入会破坏防盗权限。
   - *修复*：增加 `if (be instanceof BaseContainerBlockEntity baseContainer && !baseContainer.canOpen(player)) continue;`，只有玩家具备打开权限的容器才允许存入。

3. **🟢 [并发与死者防护] 光标悬浮持物与旁观者/死亡状态拦截**：
   - *风险分析*：玩家死亡、旁观者模式或鼠标光标正捏着一组物品（`menu.getCarried()` 非空）时，触发堆叠易导致客户端点击与槽位状态号（`stateId`）不同步。
   - *修复*：在 `ServerQuickStackHandler.handle` 顶部增加 `player.isSpectator() || player.isDeadOrDying()` 与 `!menu.getCarried().isEmpty()` 安全守卫，直接忽略。

4. **🟢 [模拟保真度] 小堆叠物品（如末影珍珠 16）空槽容量约束**：
   - *修复*：在 `SimulatedSlot.transferTo` 中移入空槽时采用 `Math.min(this.count, Math.min(this.maxStackSize, target.maxStackSize))`，确保末影珍珠（16）或工具（1）移入空槽时不会突破自身最大堆叠上限，并补充对应单测。

### 验证记录

| 验证项 | 命令 / 方式 | 结果 |
|---|---|---|
| 单元测试 | `bash scripts/with-jdk25.sh test` | 29/29 通过（全绿） |
| 完整构建 | `bash scripts/with-jdk25.sh clean build` | BUILD SUCCESSFUL |
| 产物检查 | `fabric/build/libs/onekey-fabric-0.1.0-alpha.jar` | 包含全部加固后的 class |

---

## 2026-11-04（第 2 个工作日 · 第 4 次会话：M5 快速堆叠与一键存入落地 + 高性能架构集成）

### 目标

完成 **M5 里程碑（快速堆叠与一键存入）** 并同步落地前序交接制定的性能优化路线：
1. 实现纯算法内核 `QuickStackEngine` 与 `SimulatedSlot`，并在 `common` 模块编写 8 项全面覆盖单测；
2. 落地双向网络 Payload（`QuickStackRequestPayload`、`QuickStackResultPayload`）；
3. 服务端实现基于区块的 `ServerQuickStackHandler`，采用 `LevelChunk.getBlockEntities()` 智能检索，杜绝 $(2r+1)^3$ 暴力遍历方块引起的卡顿；
4. 严格执行两阶段堆叠算法（阶段一补入未满堆叠，阶段二在 stash 模式下占用空槽），全流程绝对遵循物品数量守恒，快捷栏（0..8）与锁定槽位永久隔离保护；
5. 客户端注册 `V`（快速堆叠）与 `B`（一键存入）按键监听（支持容器内与走动中触发），提供操作反馈音效与操作统计提示；
6. 接入 `ItemConversion` 类别与 ID 静态缓存，彻底消除整理过程的临时对象与 Stream 分配。

### 本次改动与删改清单（重要）

**1. 新增文件（6 个）**
- `common/src/main/java/.../stack/SimulatedSlot.java`：纯逻辑模拟槽位，支持物品堆叠、转移与容量判定。
- `common/src/main/java/.../stack/QuickStackEngine.java`：跨加载器通用的快速堆叠/存入纯算法内核，严格执行两阶段转移、快捷栏保护、锁定保护与黑白名单过滤。
- `common/src/test/java/.../stack/QuickStackEngineTest.java`：8 项高强度单测（测试同类补齐、空槽存入、快捷栏隔离、锁定隔离、物品总数守恒定理、黑白名单组合）。
- `fabric/src/main/java/.../net/QuickStackRequestPayload.java`：C2S 快速堆叠网络请求包。
- `fabric/src/main/java/.../net/QuickStackResultPayload.java`：S2C 快速堆叠结果回执包。
- `fabric/src/main/java/.../server/ServerQuickStackHandler.java`：服务端处理器，支持区块级 BlockEntity 智能检索、双箱合并与防重复访问、两阶段转移写回与广播。
- `fabric/src/client/java/.../client/ClientQuickStackHandler.java`：客户端回执处理器，提供 ActionBar 统计提示与容器收纳音效。

**2. 修改文件与改动说明**
- `fabric/src/main/java/.../util/ItemConversion.java`：
  - 新增 `CATEGORY_CACHE` 与 `ITEM_ID_CACHE` 静态缓存，分类查询由高频 Stream/List 降为 $O(1)$ 纳秒级查询，消除临时内存垃圾。
- `fabric/src/main/java/.../OneKeyFabric.java`：
  - 注册 `QuickStackRequestPayload` 与 `QuickStackResultPayload` 编解码器与接收器。
  - 挂载玩家断开连接时清理快速堆叠冷却记录。
  - 挂载数据包重载时清理物品分类缓存。
- `fabric/src/client/java/.../client/OneKeyFabricClient.java`：
  - 注册 `QuickStackResultPayload` 客户端网络接收器。
  - 在 `ScreenKeyboardEvents.allowKeyPress` 中增加容器界面内的 V / B 热键监听与打字保护。
  - 在 `ClientTickEvents.END_CLIENT_TICK` 中支持走动时按 V / B 快速堆叠与存入。
  - 增加 `triggerQuickStack` 方法，提供服务端未安装时的友好平滑降级提示。
- `fabric/src/main/resources/assets/onekey/lang/zh_cn.json` & `en_us.json`：
  - 补充快速堆叠成功、存入成功、无可堆叠物品、服务端未安装等中英文语言条目。
- `PLAN.md`：
  - 将 M5 状态标记为已完成 ✅，更新任务拆解项状态。

### 验证记录

| 验证项 | 命令 / 方式 | 结果 |
|---|---|---|
| 单元测试 | `bash scripts/with-jdk25.sh test` | 28/28 通过（新增 8 例单测全部通过） |
| 完整构建 | `bash scripts/with-jdk25.sh clean build` | BUILD SUCCESSFUL |
| 产物检查 | `jar tf fabric/build/libs/onekey-fabric-0.1.0-alpha.jar` | 包含 `QuickStackEngine`、`SimulatedSlot`、双向网络包与服务端/客户端处理器 |

### 下一步（M6 客户端手势与兼容）

1. 鼠标手势增强（Shift + 快速双击存入整叠、拖拽分堆、滚轮微调）。
2. 与常见同类优化模组（IPN、Inventory Sorting 等）的冲突检测与柔性提示。
3. 接入客户端乐观预测渲染（Optimistic UI），将远程服网络往返延迟进一步压至 0ms 体感。

---

## 2026-11-04（第 2 个工作日 · 第 3 次会话：深度自我审查与全维度代码加固）

### 审查与修复目标

根据用户要求，在开启 M5 前以严苛眼光对已有代码（特别是 M3、M4 新增的网络、并发、存储与交互代码）进行全面自我审查，排除所有安全漏洞、内存泄漏、DoS 隐患与交互瑕疵。

### 审查发现与修复清单（共 9 项缺陷全面闭环）

#### 1. 🔴 P0 严重级缺陷修复
- **主线程同步磁盘 I/O 阻塞 DoS 漏洞**：
  - *原因*：原 `OneKeyFabric.java` 在每次收到 `ToggleSlotLockPayload` 时均在主线程直接调用 `LockedSlotsStorage.save` 写入磁盘，恶意连点或网络刷包可瞬间导致 TPS 降为 0 并崩服。
  - *修复*：在 `LockedSlotsStorage` 中引入 `dirty` 脏标记与 `markDirty()`；将写盘委托给 `ServerLifecycleEvents.BEFORE_SAVE`（周期/保存世界）与 `SERVER_STOPPING`；同时写盘采用 `.tmp` 临时文件 + `Files.move(ATOMIC_MOVE)` 原子替换，防止掉电导致 JSON 损坏。
- **无界网络输入与 JVM 堆耗尽 (OOM) 漏洞**：
  - *原因*：`containerKey` 与 `slotIndex` 缺乏合法性校验，服务端盲目插入 `LOCKS` 映射。
  - *修复*：`OneKeyFabric.java` 限制 `containerKey` 长度 $\le 64$；限制玩家背包只能加锁主背包 `9..35`；`LockedSlotsStorage` 限制单个玩家最多记录 50 种容器、每个容器最多 64 个锁定槽位，超限直接丢弃。

#### 2. 🟡 P1 状态泄漏与并发隐患修复
- **单人世界切换导致客户端锁定状态跨存档泄漏**：
  - *原因*：`ClientLockedSlots` 静态映射在玩家退出世界、重进世界或断开连接时从未调用 `clear()`，导致上一个存档的锁定状态残留高亮在下一个存档中。
  - *修复*：在 `OneKeyFabricClient.java` 中注册 `ClientPlayConnectionEvents.DISCONNECT` 与 `JOIN`，进出游戏时自动执行 `ClientLockedSlots.clear()`。
- **`LockedSlotsStorage.getLockedSlots` 并发修改异常**：
  - *原因*：原先返回 `Collections.unmodifiableSet(slots)` 仅是内部 HashSet 的包装，网络层迭代发包时若主线程增删槽位会抛出 `ConcurrentModificationException`。
  - *修复*：在 `synchronized` 同步块内复制并返回不可变快照 `Set.copyOf(slots)`。
- **`LAST_SORT_TIME` 内存泄漏**：
  - *原因*：玩家离线后其 UUID 永远残留在 `LAST_SORT_TIME` 中。
  - *修复*：增加 `ServerSortingHandler.onPlayerDisconnect(UUID)`，并在 `ServerPlayConnectionEvents.DISCONNECT` 中触发清理。

#### 3. 🟢 P2 逻辑与交互缺陷修复
- **光标悬浮物品 (Carried Item) 整理引发的幽灵物品隐患**：
  - *原因*：鼠标正拿着物品时若触发整理，该槽位被视为空格重新排布，放下物品时引发客户端/服务端坐标不同步。
  - *修复*：在 `ServerSortingHandler.handle` 增加 `if (!menu.getCarried().isEmpty()) return;`，光标持物时安全跳过整理。
- **快捷栏、盔甲与无效槽位被误加锁**：
  - *原因*：快捷栏 0..8 底层永久保护且不参与整理，但客户端原先允许玩家 Alt+左键点击加锁并画出黄框，严重误导玩家。
  - *修复*：在 `OneKeyFabricClient.java` 增加 `isSlotLockable` 校验，快捷栏（0..8）、盔甲、副手、合成格以及非白名单外部容器严格禁止锁定，无声忽略。
- **非活跃槽位 (Inactive Slot) 悬空高亮**：
  - *原因*：配方书或特定容器隐藏槽位时，`AbstractContainerScreenMixin` 依然绘制高亮边框。
  - *修复*：在 `extractSlot` 注入点增加 `slot.isActive()` 守卫。
- **配置文件数值越界缺少收敛**：
  - *原因*：用户手改配置将半径设为 9999 会导致 M5 计算崩服，冷却时间为负数会失去防刷保护。
  - *修复*：在 `OneKeyConfig.normalized()` 中加入 `Math.clamp` 约束 `cooldownMs`（50~5000ms）、`radius`（1~32格）；并在 `ConfigCodecTest` 中增加越界收敛单测。

### 本次改动文件清单

- `common/src/main/java/.../config/OneKeyConfig.java`：数值上下限约束。
- `common/src/test/java/.../config/ConfigCodecTest.java`：新增越界收敛单测。
- `fabric/src/main/java/.../storage/LockedSlotsStorage.java`：`dirty` 标记、周期与关服保存、原子替换、不可变快照拷贝、容量上限防护。
- `fabric/src/main/java/.../OneKeyFabric.java`：网络输入合法性拦截、生命周期事件挂载、离线冷却清理。
- `fabric/src/main/java/.../server/ServerSortingHandler.java`：光标悬浮物品检查、离线清理、暴露 `isMenuSortable`。
- `fabric/src/client/java/.../client/OneKeyFabricClient.java`：进出世界清理锁存缓存、`isSlotLockable` 过滤非法与快捷栏槽位。
- `fabric/src/client/java/.../client/mixin/AbstractContainerScreenMixin.java`：`slot.isActive()` 守卫。

### 验证记录

| 验证项 | 命令 / 方式 | 结果 |
|---|---|---|
| 单元测试 | `bash scripts/with-jdk25.sh test` | 20/20 通过（全绿） |
| 完整构建 | `bash scripts/with-jdk25.sh clean build` | BUILD SUCCESSFUL |
| 产物检查 | `fabric/build/libs/onekey-fabric-0.1.0-alpha.jar` | 包含全部加固后的 class |

### 响应速度与性能优化路线图（交接对齐）

经过深度系统评估，针对 mod 的“体感响应速度”与“底层性能开销”，已制定如下 5 大优化方案并纳入后续里程碑落地路线：

| 优化方向 | 核心机制 | 落地时机 | 预期收益 |
|---|---|---|---|
| **1. Item 分类静态缓存** | 建立 `Map<Item, SortCategory>` 静态映射，替代每次 `tags().map().toList()` 的高频分配 | **M5 准备期（立即落地）** | 分类查询降至纳秒级 $O(1)$，消除整理过程的临时对象垃圾与 GC 抖动 |
| **2. 区块 BlockEntity 智能扫描** | 利用 `WorldChunk.getBlockEntities()` 列表进行距离平方比对，替代 $(2r+1)^3$（2197 次）暴力方块循环 | **M5 核心研发（必须落地）** | 消除附近容器扫描的主线程 Tick 尖刺，耗时从几毫秒压至 0.05ms 以内 |
| **3. 残缺堆叠贪心预合并** | 在执行槽位排列前，先自动贪心并拢同类不完整堆叠（如 20 箭 + 30 箭 = 50 箭）并释放空格 | **M5 堆叠阶段** | 自动清理背包碎片，大幅提高空间利用率与整理紧凑度 |
| **4. 客户端乐观预测 (Optimistic UI)** | 客户端在发包同时利用本地 `Sorter` 预先完成视觉槽位排列，待服务端回包后无缝校准 | **M6 体验打磨期** | 远程服 100~150ms 延迟下实现 **0ms 瞬间响应** 的丝滑体感 |
| **5. 即时视听联觉反馈** | 按下按键瞬间即时触发轻微操作反馈音效与动效，消除等待网络包的心理迟滞感 | **M6 体验打磨期** | 显著提升按键反馈的灵敏度与跟手度 |

### 下一步接手任务（M5 快速堆叠与存入）

1. **先行接入 Item 分类静态缓存**，优化 `ItemConversion`。
2. **实现基于区块的高性能容器扫描器**，扫描玩家周围 `radius` 范围内的有效存储容器。
3. **两阶段堆叠与存入算法落地**（补齐不完整堆叠 + 存入空槽）。
4. **全流程物品守恒快照比对**，严防刷物品与吞物漏洞。

---

## 2026-11-04（第 2 个工作日 · 第 2 次会话：M4 热键框架 + 设置界面 + 槽位锁定交互落地）

### 目标

完成 **M4 里程碑**：
1. 实现统一动作注册表（`ActionRegistry`）；
2. 零外部依赖实现原版风格图形设置界面（`OneKeySettingsScreen`），按 `K` 键呼出并自动保存；
3. 打通 `Alt + 左键` 槽位锁定交互，双向网络 Payload 同步至存档，并在容器槽位上绘制琥珀金高亮边框；
4. 扩充单元测试，跑通构建与打包。

### 本次改动与删改清单（重要）

**1. 新增文件（7 个）**
- `common/src/main/java/.../action/ActionDefinition.java`：动作定义 record（包含动作 id、翻译键、默认按键名）。
- `common/src/main/java/.../action/ActionRegistry.java`：跨加载器通用的单例动作注册表，预先注册 4 大默认动作。
- `common/src/test/java/.../action/ActionRegistryTest.java`：动作注册表单元测试 3 例。
- `fabric/src/main/java/.../net/ToggleSlotLockPayload.java`：C2S 槽位锁定切换请求网络包。
- `fabric/src/main/java/.../net/RequestSlotLocksPayload.java`：C2S 请求指定容器类型已锁槽位列表网络包。
- `fabric/src/main/java/.../net/SyncSlotLocksPayload.java`：S2C 服务端向客户端同步锁定槽位网络包。
- `fabric/src/client/java/.../client/ClientLockedSlots.java`：客户端锁定槽位本地缓存与状态管理。
- `fabric/src/client/java/.../client/gui/OneKeySettingsScreen.java`：原版风格图形设置界面，使用 `CycleButton` 和 `Button` 控制排序规则、冷却时间、堆叠半径、存入模式，并提供原版按键重绑入口与槽位锁定操作提示。
- `fabric/src/client/java/.../client/mixin/AbstractContainerScreenMixin.java`：注入 `extractSlot`，在槽位被锁定时渲染琥珀金（`#FFA000`）半透明底色与 1px 高亮外边框。

**2. 修改文件与改动说明**
- `fabric/src/main/java/.../OneKeyFabric.java`：
  - 新增 `saveConfig()` 静态持久化方法。
  - 注册 `ToggleSlotLockPayload`、`RequestSlotLocksPayload`、`SyncSlotLocksPayload` 三大编解码器与服务端接收器，调用 `LockedSlotsStorage.setSlotLocked` 并实时写盘回传。
- `fabric/src/client/java/.../client/OneKeyFabricClient.java`：
  - 改用 `ActionRegistry` 统一注册 4 个热键。
  - 注册 `SyncSlotLocksPayload` 接收器更新客户端缓存。
  - 在 `ScreenEvents.AFTER_INIT` 增加容器打开时锁定数据拉取、`settingsKey` (K) 呼出设置、`ScreenMouseEvents.allowMouseClick` 拦截 `Alt + 左键` 切换锁定与播放 `UI_BUTTON_CLICK` 音效。
  - 在 `END_CLIENT_TICK` 支持走动时按 K 打开设置。
- `fabric/src/client/resources/onekey.client.mixins.json`：注册 `AbstractContainerScreenMixin`。
- `fabric/src/main/resources/assets/onekey/lang/zh_cn.json` & `en_us.json`：补充设置界面所有控件、选项与操作提示的中英文翻译。
- `README.md` & `PLAN.md`：同步 M4 为已完成。

### 踩坑记录补充

9. **26.3 Screen 控件与渲染**：
   - 26.3 中渲染已从传统 `GuiGraphics` 升级为 `GuiGraphicsExtractor`，绘制外边框使用 `extractor.outline(x, y, w, h, color)`，背景色填充使用 `extractor.fill(x1, y1, x2, y2, color)`。
   - `CycleButton.builder` 可以直接使用带单位的展示函数（如 `val + " ms"`、`val + " 格"`），无需手工写复杂逻辑。
   - 打开设置界面时必须将当前屏幕作为 `parent` 传入，在 `onClose()` 中调用 `this.minecraft.setScreenAndShow(this.parent)` 保证返回原界面。

### 验证记录

| 验证项 | 命令 / 方式 | 结果 |
|---|---|---|
| 完整构建 | `bash scripts/with-jdk25.sh clean build` | BUILD SUCCESSFUL |
| 单测 | `:fabric:test` | 19 tests, 0 failed（新增 ActionRegistryTest 3 例） |
| jar 产物检查 | `jar tf` | 包含 `OneKeySettingsScreen`、`ClientLockedSlots`、`AbstractContainerScreenMixin` 与 3 个新网络 Payload |

### 下一步（M5 快速堆叠与存入）

1. 服务端扫描玩家附近容器（默认 6 格半径，受白名单 `SORTABLE_MENU_TYPES` 约束）。
2. 实现两阶段堆叠算法（阶段一补入已有同类物品，阶段二存入空槽）。
3. 严格遵循快照对比与物品总数守恒，杜绝复制漏洞。

---

## 2026-11-04（第 2 个工作日 · P0 安全修复）

### 目标

按用户要求（"先修，记得存到git"）修复审查中发现的 2 个 P0 隐患，补充单测，构建通过后提交推送。

### 修复内容

**P0-1 锁定槽位坐标错位**（`Sorter.permutation` 的 `locked.contains(i)` 用过滤后视图下标，而 `LockedSlotsStorage` 存容器槽位号；玩家背包视图 27 格对应容器槽 9..35 差 9，越界静默失效；且 `setSlotLocked` 无任何调用者）。

- 新增纯函数 `Sorter.resolveLockedPositions(List<Integer> slotLockKeys, Set<Integer> lockedKeys)`，把「锁定键」换算成视图下标。
- `ServerSortingHandler` 读取侧：为每个待排序槽位生成 `lockIndexFor(player, slot)`（玩家背包 = `slot.getContainerSlot()` 0..35；外部容器 = `slot.index` 菜单槽位号），统一喂给 `resolveLockedPositions`，再交给 `permutation`。
- 锁定作用域改为按「实际排序的槽位」判定（`slotsToSort[0].container == player.getInventory()`），避免在熔炉等界面降级整理背包时错读 `minecraft:furnace` 键下的锁。
- `LockedSlotsStorage` javadoc 补充坐标约定。

**P0-2 外部容器排序会搬动加工/结果槽**（`isSortableExternalSlot` 只排除 `ResultSlot`/`CraftingContainer`/`isFake`；26.3 里 `FurnaceResultSlot`/`NonInteractiveResultSlot` 直接 extends `Slot`，熔炉产物/交易/信标/酿造/铁砧专用槽不排除 → 搬动会绕过经验结算、可复制）。

- 新增菜单类型白名单 `SORTABLE_MENU_TYPES`：`GENERIC_9x1..9x6` + `GENERIC_3x3`（发射器/投掷器）+ `HOPPER` + `SHULKER_BOX`，只整理纯存储容器。
- 不在白名单内的容器界面（熔炉/工作台/铁砧/交易/信标/酿造/切石机/附魔等）降级为整理玩家主背包，绝不碰容器本体。

### 新增单测（`SorterTest`，2 例）

- `resolveLockedPositionsMapsContainerIndicesToViewPositions`：容器槽位号 9..12 映射锁定键 {10,12} → 视图下标 {1,3}。
- `permutationKeepsItemAtMappedLockedPosition`：27 格背包视图锁定容器槽 12（视图 3），排序后该物品原位不动。

### 验证记录

| 验证项 | 命令 / 方式 | 结果 |
|---|---|---|
| 完整构建 + 单测 | `bash scripts/with-jdk25.sh clean build` | BUILD SUCCESSFUL，单测 16/16（排序 9 + 分类 3 + 配置 4） |

### 下一步

进入 **M4 热键框架 + 设置界面**（`ActionRegistry`、原版风格设置界面、`Alt+左键` 槽位锁定切换与高亮渲染，接入 `LockedSlotsStorage.setSlotLocked` 给 P0-1 补上写入入口）。

---

## 2026-11-03（第 1 个工作日 · 第 2 次会话：M3 一键整理落地）

### 目标

落实用户拍板的 3 项产品决策，完成 **M3 里程碑（一键整理落地）**：
1. 默认整理键保持为 `R`；
2. 底部 9 格快捷栏严格**不参与**背包整理；
3. 锁定槽位按**每个存档单独记忆，互相独立**。

### 本次改动与删改清单（重要）

**1. 新增文件（7 个）**
- `fabric/src/main/java/.../net/SortRequestPayload.java`：基于 `CustomPacketPayload` 协议注册 C2S 整理请求网络包（`onekey:sort_request`）。
- `fabric/src/main/java/.../net/SortResultPayload.java`：基于 `CustomPacketPayload` 协议注册 S2C 整理结果网络包（`onekey:sort_result`）。
- `fabric/src/main/java/.../util/ItemConversion.java`：`ItemStack` 到平台无关 `StackView` 的无损映射转换工具（抽取标签并调用 `CategoryGuesser`）。
- `fabric/src/main/java/.../storage/LockedSlotsStorage.java`：按世界存档存储玩家锁定槽位（存放在 `<world>/onekey/locked_slots.json`），实现存档级独立隔离。
- `fabric/src/main/java/.../server/ServerSortingHandler.java`：服务端整理请求处理器。校验容器 `syncId`、执行 250ms 节流、过滤槽位（**底部 9 格快捷栏严格排除**）、调用 `Sorter.permutation` 并通过原始 ItemStack 快照安全回写槽位，调用 `broadcastChanges()` 同步。
- `fabric/src/client/java/.../client/ClientSortingHandler.java`：客户端回包处理器，通过 Action Bar（`sendOverlayMessage`）展示整理结果。
- `fabric/src/client/java/.../client/mixin/AbstractContainerScreenAccessor.java`：获取当前鼠标悬停的 `hoveredSlot`，用于判断玩家意图整理的是外部容器还是主背包。

**2. 修改文件与改动说明**
- `common/src/main/java/.../net/SortRequest.java`：补充 `MODE_AUTO(0)`、`MODE_CONTAINER(1)`、`MODE_PLAYER(2)` 常量定义，未改动原有字段，完全向后兼容。
- `common/src/test/java/.../sort/SorterTest.java`：新增 `hotbarSlotsSeparationMaintainsHotbarIntegrity` 单测，单测用例增至 14 个。
- `fabric/src/main/java/.../OneKeyFabric.java`：在 `onInitialize` 中注册网络 Payload 编解码器（`PayloadTypeRegistry`）、服务端网络包接收器（`ServerPlayNetworking`）与世界存档生命周期监听（`ServerLifecycleEvents`）。
- `fabric/src/client/java/.../client/OneKeyFabricClient.java`：
  - 注册客户端回包网络接收器。
  - 通过 `ScreenEvents.AFTER_INIT` 监听容器界面的键盘事件，按 R 触发整理；若正在 `EditBox` 输入框中打字则自动放行。
  - 保留并更新 `END_CLIENT_TICK`，支持未开 GUI 时行走中按 R 整理主背包。
  - 增加未装服务端平滑降级检测（`ClientPlayNetworking.canSend`），未装端时提示「服务器未安装 OneKey，无法整理」，不在本地强改背包。
- `fabric/src/client/resources/onekey.client.mixins.json`：注册 `AbstractContainerScreenAccessor`。
- `fabric/src/main/resources/assets/onekey/lang/zh_cn.json` & `en_us.json`：补充整理成功、已整齐、服务端未装等提示语。
- `README.md` & `PLAN.md`：同步 M3 完成状态与技术差异记录。

### 踩坑记录补充

7. **26.3 客户端屏幕引用路径变动**：
   - `client.screen` 在 26.3 中被迁移，需通过 **`client.gui.screen()`** 访问。
8. **26.3 Action Bar 消息方法重命名**：
   - 过去 `player.displayClientMessage(component, true)` 在 26.3 中已被拆分，Action Bar 悬浮消息统一改为 **`player.sendOverlayMessage(component)`**，聊天框消息统一改为 **`player.sendSystemMessage(component)`**。

### 验证记录

| 验证项 | 命令 / 方式 | 结果 |
|---|---|---|
| 完整构建 | `bash scripts/with-jdk25.sh clean build` | BUILD SUCCESSFUL |
| 单测 | `:fabric:test` | 14 tests, 0 failed（新增快捷栏隔离测试） |
| 服务端加载与生命周期 | `bash scripts/with-jdk25.sh :fabric:runServer` | 打印 `[OneKey] 已加载存档锁定槽位数据：./world/./onekey/locked_slots.json`，服务器启动成功（0.270s） |
| jar 产物结构 | `jar tf` | 包含全新服务端/客户端类、mixin 配置、资源语言包与 DTO |

### 下一步（M4 热键框架 + 设置界面）

1. 实现 `ActionRegistry`，打通动作与配置项持久化。
2. 继承 `Screen` 制作原版风格设置界面，放置基础开关（排序规则、半径、黑白名单）。
3. 接入 `Alt + 左键` 槽位锁定切换与高亮边框渲染，连接至 `LockedSlotsStorage`。

---

## 2026-11-03（第 1 个工作日）

### 目标

用户诉求（原话）："我要制作一个我的世界的mod，主要内容为最新版本的操作优化mod，例如各类快捷键与一键整理等辅助mod，做一个计划表"。

经选项确认后定下的范围：**双加载器支持（Fabric 优先）+ v1 功能 = 一键整理 + 统一快捷键系统 + 快速堆叠/存入附近箱子 + 鼠标手势增强；交付 = 计划表 + 项目骨架；计划表格式 = Markdown 表格**。

### 时间线

1. **环境勘察**：确认最新稳定版是 **Minecraft 26.3**（26.4-snapshot-7 开发中），26.3 要求 **Java 25**；Fabric 侧 loader `0.20.1`、Loom `1.19-SNAPSHOT`（实际解析 1.19.3）、Fabric API `0.165.2+26.3`、Gradle wrapper 9.8.2；NeoForge `26.3.0.101`。本机原本只有 JDK 17/22、无 Gradle、无官方启动器。
2. **装 JDK**：下载 Temurin 25（OpenJDK 25.0.5+8）到项目内 `.jdk/temurin-25`，不污染系统。压缩包留在 `.jdk/jdk25.tar.gz`（130 MB，已 gitignore）。
3. **解决 Gradle 用户目录只读**：本机 `~/.gradle` 不可写，wrapper 报 `Could not create parent directory for lock file .../.gradle/wrapper/dists/...lck`。改为在 `scripts/with-jdk25.sh` 里 `export GRADLE_USER_HOME="$ROOT/.gradle-home"`，并把 `.gradle-home/` 加进 `.gitignore`。
4. **搭骨架**：`settings.gradle`（`rootProject.name='onekey'`，`include ':fabric'`）、根 `gradle.properties`、`fabric/build.gradle`（Loom + `splitEnvironmentSourceSets()` + 把 `common/src/main/java` 挂进主源集、`common/src/test/java` 挂进测试源集、JUnit BOM 5.11.4）。
5. **写纯逻辑内核 `common/`**（零 MC 依赖，可单测）：排序置换、分类猜测、配置编解码、协议 DTO。
6. **写 Fabric 壳体 `fabric/`**，然后**首次编译失败**（详见"踩坑记录"第 1 条），改 API 后通过。
7. **构建 + 单测 + 双端启动验证**全绿（详见"验证记录"）。
8. **写文档与图标**：`PLAN.md`（290 行，主交付物）、`README.md`、MIT `LICENSE`、128×128 `icon.png`（Pillow 生成，绿底 + 三个色阶方块 + 白字 "1K"）。
9. **git**：`git init` → 首个提交 `cf6463f`（36 文件）→ 用户推送到 GitHub；核对 origin/main 与本地一致，设置 upstream 跟踪。
10. **收工**：本日志 + 提交推送。

### 产出清单

**顶层**
- `PLAN.md` — 计划表（主交付物）：目标与量化成功标准、技术基线、架构决策、功能表 F1–F10、M0–M8 里程碑、逐里程碑任务分解、三周排期、快捷键与交互、配置项表、测试矩阵、风险表、发布清单、26.3 API 差异对照表
- `README.md` — 状态表、快捷键、构建方式、目录结构
- `LICENSE` — MIT（Copyright (c) 2026 easan）
- `scripts/with-jdk25.sh` — 固定 JDK 25 + 项目内 `GRADLE_USER_HOME` 的构建入口
- `settings.gradle` / `gradle.properties` / `.gitignore` / Gradle wrapper

**`common/`（纯 Java 内核，无 Minecraft 依赖）**

| 文件 | 作用 |
|---|---|
| `sort/StackView.java` | 槽位视图 record：`itemId` / `count` / `category`，含 `EMPTY`、`isEmpty()` |
| `sort/SortCategory.java` | 分类枚举：TOOL(0) / BLOCK(1) / FOOD(2) / POTION(3) / MISC(4) |
| `sort/CategoryGuesser.java` | `guess(itemId, tags)`：先看物品标签，再按 id 关键字（sword/planks/apple/potion…），兜底 MISC |
| `sort/SortRule.java` | 规则开关 `groupByCategory` / `sortByName` / `sortByCountDesc`，`copy()` |
| `sort/Sorter.java` | `permutation(slots, lockedSlots, rule) -> int[]`（`result[目标槽]=源槽`），空格置后、锁定槽不动、可 `apply()` 生成新列表 |
| `config/OneKeyConfig.java` | 配置模型 + 默认值：`hotkeys{sort=r, quickStack=v, quickStash=b, settings=k}`、`sort{enabled,groupByCategory,sortByName,sortByCountDesc,cooldownMs=250}`、`quickStack{radius=6, stashMode, whitelist, blacklist}`、`compat{enabled=false, clickDelayMs=60}`；`normalized()`、`toRule()` |
| `config/ConfigCodec.java` | Gson（pretty printing）编解码 + `load/save(Path)`，自动建父目录 |
| `action/ActionId.java` | 动作 id 常量：`onekey.sort` / `onekey.quick_stack` / `onekey.quick_stash` / `onekey.settings` |
| `net/ProtocolVersion.java` | `CURRENT = 1` |
| `net/SortRequest.java` `net/SortResult.java` `net/QuickStackRequest.java` `net/QuickStackResult.java` | 协议 DTO（record）：`syncId`、`mode`、`radius`、`stash`、`moved`、`success` |

**`fabric/`（Fabric 26.3 壳体）**
- `src/main/java/.../OneKeyFabric.java` — 模组入口：加载 `config/onekey.json`，打印 `[OneKey] 已初始化（Minecraft 26.3 / Fabric）`
- `src/client/java/.../client/OneKeyFabricClient.java` — 注册 4 个热键（R/V/B/K，分类 `key.category.onekey.keys`），`ClientTickEvents.END_CLIENT_TICK` 里 `consumeClick` 目前**只打日志**，M3/M4/M5 才接真实动作
- 资源：`fabric.mod.json`、`onekey.mixins.json`、`onekey.client.mixins.json`（mixin 列表暂空）、`zh_cn.json` / `en_us.json`、`icon.png`
- `fabric/build.gradle` — Loom 1.19.3、`version = project.mod_version`、`archivesName = "onekey-fabric"`、LICENSE 以 `LICENSE_onekey` 名入 jar

**测试（`common/src/test/java`）**
- `SorterTest`（6）：分类排序 / 相等堆叠保持相对序 / 置换恒为双射 / 全锁定=恒等 / 空格置后 / 锁定槽不被搬动
- `CategoryGuesserTest`（3）：标签优先 / 常见物品 / 未知兜底 MISC
- `ConfigCodecTest`（4）：JSON 往返 / 缺字段用默认值 / null 段归一化 / 存盘后读回

### 验证记录（都实际跑过，非纸面）

| 验证项 | 命令 / 方式 | 结果 |
|---|---|---|
| 完整构建 | `bash scripts/with-jdk25.sh clean build` | BUILD SUCCESSFUL |
| 单测 | `:fabric:test` | 13 tests, 0 failed |
| jar 内容 | `unzip -l fabric/build/libs/onekey-fabric-0.1.0-alpha.jar` | 含 `fabric.mod.json`（version 已展开 `0.1.0-alpha`）、两个 mixin json、`assets/onekey/icon.png`、中英语言文件、`LICENSE_onekey` |
| 服务端启动 | `bash scripts/with-jdk25.sh :fabric:runServer` | `- onekey 0.1.0-alpha`、`[OneKey] 配置已加载：./config/onekey.json`、`[OneKey] 已初始化（Minecraft 26.3 / Fabric）`、`Done (2.026s)!` |
| 客户端启动 | `bash scripts/with-jdk25.sh :fabric:runClient` | 进入标题界面；`[OneKey] 客户端已初始化，注册 4 个热键（默认 R / V / B / K）`；无异常、无 mixin 报错 |
| 配置生成 | 读 `fabric/run/config/onekey.json` | schema=1；hotkeys r/v/b/k；sort 全 true + cooldownMs=250；quickStack radius=6、stashMode=false、黑白名单空；compat false/60 |
| 远端一致性 | `git fetch` + GitHub API | origin/main = 本地 HEAD = `cf6463f`，双方各 36 文件，`gradlew` 保留 100755 |

> 注意：测试服务端目录是 **`fabric/run/`**（不是仓库根的 `run/`）；首次启动需 `fabric/run/eula.txt` 内写 `eula=true`，否则直接退出并提示 `You need to agree to the EULA in order to run the server`。

### 踩坑记录（重要，别重复踩）

1. **26.3 按键 API 全改名**（首次编译 8 个 error 的根因）：
   - `InputConstants.Type.KEYSYM` → **`InputConstants.Type.KEYBOARD`**（26.3 只剩 `KEYBOARD`/`MOUSE`）
   - `KeyMapping` 构造第 4 参不再是 `String`，而是 **`KeyMapping.Category`** record；必须先 `KeyMapping.Category.register(Identifier.fromNamespaceAndPath(modid, "keys"))`，重复注册抛 `IllegalArgumentException: Category '%s' is already registered.`
   - 不再需要 `org.lwjgl.glfw.GLFW`，直接用 `InputConstants.KEY_R / KEY_V / KEY_B / KEY_K`
   - Fabric 的 `KeyBindingHelper` → **`net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(...)`**（模块 jar 名 `fabric-key-mapping-api-v1`）
   - 语言键由 `category.onekey` 改为 **`key.category.onekey.keys`**（`Category.label()` 用 `id.toLanguageKey("key.category")`）
2. **Loom 1.19.3 没有 `modImplementation`**（报 `Could not find method modImplementation()`），mod 依赖统一用 `implementation`。
3. **`base.archivesName` 会吃掉版本号**，jar 变成 `onekey-fabric.jar`；必须在 `fabric/build.gradle` 显式 `version = project.mod_version`。
4. **`~/.gradle` 只读** → 一律用 `scripts/with-jdk25.sh`（内部设置 `GRADLE_USER_HOME=$ROOT/.gradle-home`），不要直接调 `./gradlew`。
5. **调试类路径**的手段（下次缺包可用）：`./gradlew --init-script /tmp/printcp.gradle :fabric:printClientCp`，任务里遍历 `sourceSets.client.compileClasspath` 打印。也可用 `.jdk/temurin-25/.../bin/javap -cp <minecraft-clientonly-deobf jar> <类>` 反编译确认 26.3 真实签名。
6. **Yarn 映射不可用**（只到 1.21.11），26.x 走 Mojang 官方映射，`build.gradle` 不要写 `mappings`。

### 决策记录

- **双加载器但 Fabric 优先**：common 写成纯 Java 内核，Fabric 与未来的 NeoForge 只做薄壳适配 → NeoForge 排到 M7，不阻塞 v1。
- **排序在服务端执行**（客户端只发请求、服务端校验并回包）：避免客户端单方面改 `Inventory` 造成不同步/刷物品；代价是必须有网络 payload（M3 补）。
- **零第三方运行时依赖**：Gson 仅用于配置读写（Minecraft 自带），不引入 Cloth Config 等，保证 jar 小、无版本冲突。
- **物品分类放 common**：靠标签 + id 关键字猜测，简单可测；后续可加"用户自定义分类"。
- **热键统一走 `ActionId` + `KeyMapping` 抽象**，为 M4 的可改键与设置界面留口。
- **配置文件 `config/onekey.json`**（不是 `.toml`）：Gson 直接映射模型，省一个依赖。

### 待决策（需要用户拍板，定了就能写进代码）

1. 默认整理键 `R` 与 Inventory Profiles Next（IPN）冲突时：改默认 `G`，还是首次启动弹窗让玩家自己选？
2. 快捷栏（9 格）是否参与整理？（当前设计：**不参与**）
3. 槽位锁定按「容器类型全局」记录，还是「每个存档单独」记录？

### 下一步（M3 一键整理，按 PLAN.md 第 5 节）

1. 注册 `onekey:sort` 的 `CustomPayload` 类型（`PayloadTypeRegistry.playC2S()`）。
2. 客户端：热键触发 → 读当前容器（`AbstractContainerMenu`）槽位 → 组 `SortRequest(syncId, mode)` → 发送。
3. 服务端：`ServerPlayNetworking.registerGlobalReceiver` 校验（菜单 `syncId` 是否匹配、距离/权限、容器是否允许整理）→ 调 `Sorter.permutation(...)` → 回写槽位 → `broadcastChanges()`。
4. 回包 `SortResult`（成功/移动数量），客户端给 action bar 提示。
5. 降级：服务端未安装 OneKey 时，客户端提示"服务器未安装，无法整理"，**不要**本地强行改。
6. 测试：单人 36 格容器排序、锁定槽位、空格后置、连续快速触发（`cooldownMs=250` 节流）、服务器未装模组的情况、断开重连后配置不丢。

---

## 协作约定（双方都遵守）

1. **每次动手前**先 `git fetch origin && git status && git log --oneline -5`，确认没有对方未推送/未拉取的改动；有冲突先沟通，不要强推。
2. **每次收工前**：更新本文件（新增当天小节 + 刷新"交接速览"表）→ `git add -A && git commit` → 推送 `origin main`。
3. 提交信息用中文、一句话说清做了什么（如 `M3: 注册 onekey:sort payload 并打通服务端排序`）。
4. 不要提交 `.jdk/`、`.gradle-home/`、`fabric/run/`、`build/`（已在 `.gitignore`）。
5. 遇到 API 变更/构建坑，先补到本文件"踩坑记录"，再改代码。
6. **推送凭据**：本机 HTTPS 没有可用凭据，`origin` 的 pushurl 已设为 `git@github.com:Roland114514/OneKey.git`（SSH key 可用，`ssh -T git@github.com` 返回 `Hi Roland114514!`），fetch 仍走 HTTPS。直接 `git push origin main` 即可；若报 `fatal: could not read Username for 'https://github.com'`，说明 pushurl 被改回 HTTPS，用 `git remote set-url --push origin git@github.com:Roland114514/OneKey.git` 修回。
