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
| 最新提交 | `8a5bffa`（fix: 修正锁定槽位坐标错位并限制外部容器白名单，P0×2） |
| 构建状态 | ✅ `clean build` 通过，产物 `fabric/build/libs/onekey-fabric-0.1.0-alpha.jar` |
| 单测状态 | ✅ 16/16 通过（排序 9 + 分类 3 + 配置 4） |
| 运行验证 | ✅ 开发服务端正常加载锁定槽位；P0 修复后 clean build 通过、单测 16/16 |
| 里程碑 | M0 环境 ✅ / M1 骨架 ✅ / M2 内核+配置+协议 ✅ / **M3 一键整理 ✅** / **M4 热键与设置界面 ← 下一步** |
| 阻塞项 | 无（用户已拍板 3 项产品决策：R键保持、快捷栏不参与、锁定槽位存档级隔离） |
| 生产可用性 | 🟡 核心一键整理网络与执行链路已全通，待 M4 设置界面与 M5 堆叠逻辑 |

**接手第一件事**：`git fetch origin && git status && git log --oneline -5`，确认没有对方未推送/未拉取的改动，再读 `PLAN.md` 第 5 节 M4 任务清单。

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
