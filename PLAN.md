# OneKey · Minecraft 26.3 操作优化模组开发计划表

> 立项日期：2026-11-03　目标版本：Minecraft 26.3（Java 25）　首发加载器：Fabric，M7 补 NeoForge
> 代码仓库：`/Users/easan/Documents/我的世界mod`（Gradle 多模块：`common` 纯逻辑 + `fabric` 平台壳）

---

## 1. 目标与成功标准

**一句话目标**：做一款「装完就能少点几百次鼠标」的轻量操作优化 mod，v1.0 覆盖整理、热键、批量堆叠/存入三类高频痛点。

| 维度 | 成功标准（v1.0） |
| --- | --- |
| 功能 | 一键整理、快速堆叠、存入附近容器、统一热键 + 图形设置界面全部可用 |
| 兼容 | 单人 / 原版服务器（服务端未装 mod 时不报错、自动降级）；与 JEI、REI、Iris 不冲突 |
| 性能 | 整理 36 格背包耗时 < 2 ms，无每 tick 轮询；空闲时零额外内存分配 |
| 质量 | 内核单测覆盖排序/分类/配置/协议，`./gradlew build` 零警告失败 |
| 体积 | jar < 200 KB，运行时依赖 0（Gson 仅构建期用于配置序列化，Minecraft 自带） |
| 分发 | Modrinth + CurseForge + GitHub Release 三处同版本发布，带图标与中英文简介 |

**非目标（v1.0 明确不做）**：改变方块/合成/数值；自动战斗、自动挖掘等作弊向功能；自带 UI 框架或依赖 Cloth Config；Fabric 与 NeoForge 之外的第 4 种加载器。

---

## 2. 技术基线（已实测确认）

| 项 | 版本 / 取值 | 确认方式 |
| --- | --- | --- |
| Minecraft | 26.3（稳定版；26.4-snapshot-7 开发中） | Mojang version manifest |
| Java | 25（`javaVersion.majorVersion=25`，java-runtime-epsilon） | 26.3 version json |
| Fabric Loader | 0.20.1 | fabricmc.net/develop |
| Fabric API | 0.165.2+26.3 | fabricmc.net/develop |
| Fabric Loom | 1.19-SNAPSHOT | 官方模板 gradle.properties |
| Gradle | 9.8.2（wrapper 已入库） | 模板 wrapper |
| 映射 | Mojang 官方映射（Yarn 仅到 1.21.11，26.x 无 Yarn） | 模板无 `mappings` 声明，代码用 `net.minecraft.resources.Identifier` |
| NeoForge | 26.3.0.101（M7 用 ModDevGradle 生成 MDK） | NeoForge 官网 |
| 本机环境 | macOS 15.4.1 arm64；JDK 25 已装 `.jdk/temurin-25`；Gradle 缓存改到 `.gradle-home/`（`~/.gradle` 只读） | 实测 |

**架构决策（含理由）**

| 决策 | 选择 | 理由 |
| --- | --- | --- |
| 模块拆分 | `common` 纯逻辑 + `fabric` 平台壳 | 排序/配置/协议可脱离 MC 单测，M7 NeoForge 直接复用，避免二次实现 |
| 排序执行侧 | 服务端执行，客户端只发请求 | 防作弊、多人一致；单人时集成服天然可用 |
| 依赖策略 | 零第三方运行时依赖 | 竞品 IPN 4321 万下载、Inventory Sorting 374 万下载（Modrinth 26.3 整理类共 28 个），差异化只能靠「轻 + 双加载器 + 统一热键」 |
| 配置格式 | `config/onekey.json`，Gson pretty | MC 自带 Gson，无额外依赖；人类可读、易排障 |
| 网络协议 | 自有 `onekey:sort` / `onekey:quick_stack` payload，`ProtocolVersion.CURRENT=1` | 不依赖 Fabric API 的网络抽象，M7 迁移成本最低 |
| 热键 | 基于 `KeyMapping` + `KeyBindingHelper`，动作抽象 `ActionId` | 统一入口，设置界面与按键绑定共用一份动作注册表 |

---

## 3. 功能范围

| 编号 | 功能 | 说明 | 版本 |
| --- | --- | --- | --- |
| F1 | 一键整理 | 按「分类 → 名称 → 数量」排序；空格后置；锁定槽位（鼠标悬停/手动锁定）不动 | v1.0 |
| F2 | 统一快捷键系统 | 4 个默认热键 + 全部可重绑 + 动作注册表，后续功能零成本接入 | v1.0 |
| F3 | 图形设置界面 | 热键、排序规则、堆叠半径、黑白名单可视化修改 | v1.0 |
| F4 | 快速堆叠 | 背包物品自动并入附近容器中同类堆叠 | v1.0 |
| F5 | 存入附近容器 | 整叠存入附近容器，支持白/黑名单 | v1.0 |
| F6 | 鼠标手势增强 | 拖拽整叠、Shift 快速移动、滚轮分堆 | v1.0（M5–M6） |
| F7 | 容器搜索/过滤高亮 | 打开容器时按名字过滤槽位 | v1.1 |
| F8 | 排序预设 | 多套排序方案（按分类/按稀有度/自定义分组）切换 | v1.1 |
| F9 | 服务器白名单同步 | 服务端下发允许的整理规则 | v1.1 |
| F10 | NeoForge 端 | 同一 `common` 内核 + NeoForge 壳 | v1.0（M7） |

---

## 4. 里程碑总表

| 里程碑 | 周期 | 交付物 | 验收标准 | 状态 |
| --- | --- | --- | --- | --- |
| **M0 环境与工具链** | D1（11-03） | JDK 25、Gradle wrapper、`scripts/with-jdk25.sh`、`.gitignore` | `./gradlew --version` 输出 Gradle 9.8.2 / JVM 25；缓存写入项目内 `.gradle-home/` | ✅ 完成 |
| **M1 项目骨架** | D1（11-03） | `settings.gradle`、`gradle.properties`、`fabric/build.gradle`、`fabric.mod.json`、双 mixin 配置、中英文语言文件、图标、README/LICENSE | `./gradlew build` 通过；产物 jar 可被 Fabric 识别（`fabric.mod.json` 校验通过） | ✅ 完成 |
| **M2 内核：排序 + 配置 + 协议** | D1（11-03） | `common` 下 sort/config/action/net 包；JUnit 5 单测 13 例 | 全部单测通过（排序双射、锁定槽位、空格后置、配置往返/缺省/null 归一） | ✅ 完成 |
| **M3 一键整理落地** | D2–D4（11-04 ~ 11-06） | 客户端 `KeyMapping` → `SortRequest` payload；服务端 `ServerPlayNetworkHandler` 收包、排序、回包；`SortResult` 提示 | 单人/专用服务器均能整理；锁定槽位不动；连续按有 250 ms 冷却；未装服务端时不崩、提示「服务端未安装」 | ⬜ 未开始 |
| **M4 热键框架 + 设置界面** | D4–D6（11-06 ~ 11-08） | `ActionRegistry`（动作 ↔ 热键 ↔ 翻译键）；`Screen` 基类的设置界面（排序规则开关、堆叠半径、黑白名单、按键重绑入口） | 4 个热键全部可重绑并持久化；设置改动即时生效；界面可用键盘导航、支持中英文 | ⬜ 未开始 |
| **M5 快速堆叠 / 存入** | D6–D9（11-08 ~ 11-11） | 服务端扫描半径内容器（默认 6 格）、按规则合并/存入；`QuickStackRequest/Result` 接线 | 半径内同类物品正确合并；白/黑名单生效；无容器时给出提示；不复制物品（回归测试） | ⬜ 未开始 |
| **M6 客户端手势 + 兼容** | D9–D11（11-11 ~ 11-13） | 拖拽整叠、Shift 快速移动、滚轮分堆；与 IPN/Inventory Sorting 的冲突检测与提示 | 手势操作与原版行为一致；检测到同类 mod 时给出可关闭的提示；不与 JEI/REI 抢鼠标事件 | ⬜ 未开始 |
| **M7 NeoForge 适配** | D11–D14（11-13 ~ 11-16） | `neoforge/` 模块（ModDevGradle）、事件与网络适配层 | `./gradlew :neoforge:build` 通过；同一套 `common` 内核；功能对齐 Fabric 端 | ⬜ 未开始 |
| **M8 发布** | D14–D15（11-16 ~ 11-17） | Modrinth/CurseForge 页面、GitHub Release、变更日志、双加载器 jar | 三处同版本可下载；干净客户端 + 原版服务器实测通过；README 截图齐全 | ⬜ 未开始 |

> D=工作日。总工期约 15 个工作日（3 周），M7/M8 可并行压缩到 2.5 周。

---

## 5. 逐里程碑任务分解

### M1 项目骨架（收尾项）

| # | 任务 | 产出 | 状态 |
| --- | --- | --- | --- |
| 1.1 | Gradle 多模块配置（`common` 源码并入 `fabric` 源集与测试源集） | `fabric/build.gradle` | ✅ |
| 1.2 | `fabric.mod.json`（入口、依赖、mixin、图标） | 资源文件 | ✅ |
| 1.3 | 双源集 mixin 配置（`onekey.mixins.json` / `onekey.client.mixins.json`，`JAVA_25`） | 资源文件 | ✅ |
| 1.4 | 中英文语言文件（4 个热键 + 分类） | `assets/onekey/lang/*.json` | ✅ |
| 1.5 | 128×128 图标 | `assets/onekey/icon.png` | ✅ |
| 1.6 | 首次构建验证 + 修复编译问题（26.3 `InputConstants` / `KeyMapping.Category` / Fabric API 更名） | 构建日志 | ✅ |

### M2 内核（收尾项）

| # | 任务 | 产出 | 状态 |
| --- | --- | --- | --- |
| 2.1 | `StackView` / `SortCategory` / `CategoryGuesser` | `common/sort` | ✅ |
| 2.2 | `Sorter.permutation`（稳定排序 + 锁定槽位 + 空格后置 + 置换双射） | `common/sort` | ✅ |
| 2.3 | `OneKeyConfig` / `ConfigCodec`（默认值、null 归一、缺字段兼容、建目录） | `common/config` | ✅ |
| 2.4 | `ActionId` + 协议 DTO（`SortRequest/Result`、`QuickStackRequest/Result`、`ProtocolVersion`） | `common/action`、`common/net` | ✅ |
| 2.5 | JUnit 5 单测 13 例 | `common/src/test` | ✅ 全绿 |
| 2.6 | 协议版本号与向后兼容约定写入 README | 文档 | ⬜（M3 一并补） |

### M3 一键整理（详细任务）

| # | 任务 | 技术要点 |
| --- | --- | --- |
| 3.1 | 定义 payload | `CustomPayload` + `PayloadTypeRegistry`，通道 `onekey:sort`；字段 `syncId`、`mode` |
| 3.2 | 客户端发请求 | 仅在 `client.player.containerMenu.containerId == syncId` 时发送，防错位 |
| 3.3 | 服务端校验 | 校验玩家打开的容器与 `syncId` 一致、`cooldownMs` 内拒绝重复请求 |
| 3.4 | 服务端执行排序 | 遍历 `AbstractContainerMenu.slots`，跳过锁定槽位与快捷栏（可配置），调用 `Sorter.apply`，用 `slot.set` 写回 |
| 3.5 | 回包与提示 | 发送 `SortResult(success, moved)`，客户端 `actionBar` 提示「已整理 N 格」 |
| 3.6 | 降级路径 | 服务端未装：请求发出后 1 s 无回包 → 本地提示，不重试风暴 |
| 3.7 | 测试 | 单人整理 36 格、锁定 3 格、满背包、空背包、只读容器（熔炉/工作台） |

### M4 热键框架 + 设置界面（详细任务）

| # | 任务 | 技术要点 |
| --- | --- | --- |
| 4.1 | `ActionRegistry` | 单例注册表：`ActionId` ↔ 默认键 ↔ 翻译键 ↔ 回调；`KeyMapping` 由此批量生成 |
| 4.2 | 配置热键落地 | 启动时读 `hotkeys.*`，注册为 `KeyMapping`；改配置后重载需重建 `KeyMapping`（或提示重启） |
| 4.3 | 设置界面 | 继承 `Screen`，4 个分区（热键/排序/堆叠/兼容），纯原版控件（`Button`、`CycleButton`、`EditBox`） |
| 4.4 | 持久化 | `ConfigCodec.save` 到 `config/onekey.json`，界面关闭即写盘 |
| 4.5 | 键盘/手柄导航 | `Screen` 原生 Tab 焦点；`CycleButton` 支持方向键 |
| 4.6 | 测试 | 中英文切换、重绑冲突提示、非法半径（0/64）钳制 |

### M5 快速堆叠 / 存入（详细任务）

| # | 任务 | 技术要点 |
| --- | --- | --- |
| 5.1 | 容器扫描 | 以玩家为中心 `radius` 立方体，取 `ChestBlockEntity`/`BarrelBlockEntity`/`ShulkerBoxBlockEntity` 等 `Container` |
| 5.2 | 堆叠算法 | 两阶段：先填未满堆叠（同物品且 `count < maxCount`），再找空槽；受黑白名单约束 |
| 5.3 | 存入算法 | `stashMode=true` 存全部可存；`false` 仅补容器里已存在的物品类型 |
| 5.4 | 物品安全 | 写回前先算目标快照，避免同物品在扫描中被反复移动；单次操作上限（默认 1024 件） |
| 5.5 | 回包与统计 | `QuickStackResult(moved)` → 客户端提示「已堆叠 N 件 / 存入 N 格」 |
| 5.6 | 测试 | 半径 0/6/16、容器满、容器被破坏、双箱、潜影盒、黑白名单组合 |

### M6 手势 + 兼容（详细任务）

| # | 任务 | 技术要点 |
| --- | --- | --- |
| 6.1 | 拖拽整叠 | 鼠标按下 + 拖过槽位 = 逐格放 1 个（原版已支持），补充「拖拽整叠」修饰键 |
| 6.2 | Shift 快速移动 | 容器 ↔ 背包双向快速移动（原版已有，确认不重复实现） |
| 6.3 | 滚轮分堆 | 手持物品滚轮调整单次放置数量 |
| 6.4 | 同类 mod 检测 | 启动时检测 `inventoryprofilesnext`、`inventory-sorting` 等 mod id，给出一次性提示（可关） |
| 6.5 | 点击模拟兼容层 | `compat.clickDelayMs` 串行化模拟点击，避免与 IPN 的点击注入打架 |
| 6.6 | 测试 | 与 JEI/REI 同开、与 IPN 同开、服务器反作弊（模拟点击频率） |

### M7 NeoForge 适配（详细任务）

| # | 任务 | 技术要点 |
| --- | --- | --- |
| 7.1 | 生成 MDK | ModDevGradle，`neoforge/` 子模块，复用 `common` 源集 |
| 7.2 | 入口与事件 | `@Mod` 主类、`FMLClientSetupEvent` 注册热键、`RegisterPayloadHandlersEvent` 注册 payload |
| 7.3 | 网络适配 | NeoForge `CustomPacketPayload` 与 Fabric payload 字段一一对应，共用 `ProtocolVersion` |
| 7.4 | 排序执行 | 复用 `Sorter`，仅替换容器读写适配 |
| 7.5 | 测试 | 两加载器同一存档/服务器互不影响；配置文件路径一致 |

---

## 6. 周排期（按自然周）

| 周次 | 日期 | 主要工作 | 周末检查点 |
| --- | --- | --- | --- |
| W1 | 11-03 ~ 11-09 | M0、M1、M2 收尾；M3 一键整理打通；M4 热键框架 | 单人可整理背包，服务端回包正常 |
| W2 | 11-10 ~ 11-16 | M5 快速堆叠/存入；M6 手势与兼容；M7 NeoForge 起步 | 半径堆叠可用，NeoForge 能编译 |
| W3 | 11-17 ~ 11-23 | M7 收尾；M8 发布；文档/截图/变更日志 | 三平台发布 v1.0.0 |

缓冲：每个里程碑预留 20% 时间，M3/M5 是风险最高项（涉及服务端与物品安全）。

---

## 7. 交互与快捷键设计

| 动作 | 默认键 | 触发条件 | 行为 |
| --- | --- | --- | --- |
| `onekey.sort` | `R` | 任意容器界面打开 | 整理当前容器；锁定槽位与快捷栏跳过 |
| `onekey.quick_stack` | `V` | 任意时刻（有容器在半径内） | 背包 → 附近容器，只并入同类堆叠 |
| `onekey.quick_stash` | `B` | 任意时刻（有容器在半径内） | 背包 → 附近容器空槽，按白/黑名单 |
| `onekey.settings` | `K` | 任意时刻 | 打开 OneKey 设置界面 |

**槽位锁定交互**：`Alt + 左键` 切换锁定；锁定槽位在界面中以高亮边框标记；锁定状态存于 `config/onekey.json` 的 `sort.lockedSlots`（按容器类型 + 槽位索引）。

---

## 8. 配置项（`config/onekey.json`）

| 键 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `schema` | int | 1 | 配置版本，用于迁移 |
| `hotkeys.sort` | string | `key.keyboard.r` | 整理热键 |
| `hotkeys.quickStack` | string | `key.keyboard.v` | 快速堆叠热键 |
| `hotkeys.quickStash` | string | `key.keyboard.b` | 存入热键 |
| `hotkeys.settings` | string | `key.keyboard.k` | 设置界面热键 |
| `sort.enabled` | bool | true | 总开关 |
| `sort.groupByCategory` | bool | true | 按分类分组 |
| `sort.sortByName` | bool | true | 组内按名称排序 |
| `sort.sortByCountDesc` | bool | true | 同组内按数量降序 |
| `sort.cooldownMs` | int | 250 | 请求冷却（防连点） |
| `sort.lockedSlots` | map | `{}` | 按容器类型记录的锁定槽位 |
| `quickStack.radius` | int | 6 | 容器扫描半径（0–16，越界钳制） |
| `quickStack.stashMode` | bool | false | false=只并入同类堆叠（快速堆叠）；true=整叠存入（一键存入） |
| `quickStack.whitelist` | list | `[]` | 允许的容器；空 = 不限制 |
| `quickStack.blacklist` | list | `[]` | 禁止的容器（优先于白名单） |
| `compat.enabled` | bool | false | 点击模拟兼容层开关 |
| `compat.clickDelayMs` | int | 60 | 模拟点击间隔 |

---

## 9. 测试矩阵

| 层 | 范围 | 方式 | 通过标准 |
| --- | --- | --- | --- |
| 单元测试 | 排序置换、分类猜测、配置编解码 | JUnit 5（`common/src/test`） | 13 例全绿，`./gradlew test` |
| 集成测试 | 请求 → 服务端排序 → 回包 | 单人 `runClient` + 日志断言 | 3 类容器（背包/箱子/潜影盒）无错位、无物品丢失 |
| 兼容测试 | JEI、REI、IPN、Iris | 手动组合启动 | 无崩溃、无热键抢占、无界面重叠 |
| 多人测试 | 专用服务器 + 2 客户端 | 本地 `runServer` | 两人同时整理互不干扰；未装端降级提示正确 |
| 边界测试 | 空背包、满背包、只读容器、半径 0/16、黑白名单冲突 | 手动 + 单测 | 全部给出明确提示，不崩、不复制物品 |
| 性能测试 | 整理 36/54 格 | JFR / 日志计时 | 单次 < 2 ms，无 GC 峰值 |

---

## 10. 风险与对策

| 风险 | 概率 | 影响 | 对策 |
| --- | --- | --- | --- |
| 26.x 映射/API 变动（无 Yarn，官方映射命名漂移） | 中 | 编译失败 | 锁定 26.3 与模板 commit；升级前先跑 `./gradlew build` 基线 |
| 服务端未装 mod 导致玩家困惑 | 高 | 体验差 | 1 s 超时降级 + 明确提示「服务器未安装 OneKey」；README 标注 |
| 物品复制/丢失漏洞 | 低 | 致命 | 所有写回走快照对比；M5 单测覆盖满容器/双箱；发布前双人实测 |
| 与 IPN/Inventory Sorting 热键冲突 | 高 | 差评 | 启动检测 + 一次性提示；默认键避开 `R`（IPN 默认 `R`）→ 若冲突则默认改 `G`，设置界面可改 |
| NeoForge MDK 生成失败 | 中 | 延期 | M7 前先手工验证 ModDevGradle 版本与 Java 25 兼容；必要时 M8 先只发 Fabric |
| `~/.gradle` 只读（本机） | 已发生 | 构建失败 | `scripts/with-jdk25.sh` 固定 `GRADLE_USER_HOME=.gradle-home`，已入 `.gitignore` |

---

## 11. 发布清单（M8）

- [ ] `./gradlew build` 绿；`./gradlew test` 13 例绿
- [ ] `fabric.mod.json` 依赖版本与实测一致（`minecraft ~26.3`、`java >=25`）
- [ ] jar 内含 `LICENSE_onekey-fabric`、图标、中英文语言文件
- [ ] Modrinth 页面：标题/简介/图标/截图 3 张/版本表（26.3 + Fabric）
- [ ] CurseForge 页面同版本同步
- [ ] GitHub Release：tag `v1.0.0`，附 jar 与 CHANGELOG
- [ ] CHANGELOG：F1–F6 列表 + 已知问题（服务端需装、IPN 热键冲突提示）
- [ ] 干净客户端 + 原版服务器实测录像/截图归档

---

## 12. 当前进度与下一步

**已完成（2026-11-03 当日验证）**

| 验证项 | 命令 / 方式 | 结果 |
| --- | --- | --- |
| 完整构建 | `bash scripts/with-jdk25.sh clean build` | BUILD SUCCESSFUL，产物 `fabric/build/libs/onekey-fabric-0.1.0-alpha.jar`（约 32 KB） |
| 单元测试 | 构建内 `:fabric:test` | 13 例全部 PASSED（Sorter 6 / CategoryGuesser 3 / ConfigCodec 4） |
| 服务端加载 | `bash scripts/with-jdk25.sh :fabric:runServer` | `onekey 0.1.0-alpha` 出现在 mod 列表，`[OneKey] 已初始化`，`Done (2.026s)`，生成 `config/onekey.json` |
| 客户端加载 | `bash scripts/with-jdk25.sh :fabric:runClient` | 资源管理器加载 `onekey`，`[OneKey] 客户端已初始化，注册 4 个热键`，进入标题界面无异常 |
| jar 内容 | `unzip -l` | 含 `fabric.mod.json`（版本已展开）、`onekey.mixins.json`、`onekey.client.mixins.json`、`assets/onekey/icon.png`、中英文语言文件、`LICENSE_onekey` |

**过程中确认的 26.3 API 差异（M3 起需持续注意）**

| 1.21.x 写法 | 26.3 写法 |
| --- | --- |
| `InputConstants.Type.KEYSYM` | `InputConstants.Type.KEYBOARD` |
| `new KeyMapping(name, type, key, "category.string")` | `new KeyMapping(name, type, key, KeyMapping.Category)`，分类需 `KeyMapping.Category.register(Identifier)` 注册 |
| `GLFW.GLFW_KEY_R` | `InputConstants.KEY_R`（避免额外引入 lwjgl 常量） |
| `net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper` | `net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping` |
| 语言键 `category.onekey` | `key.category.onekey.keys`（`Identifier.toLanguageKey("key.category")`） |
| Loom `modImplementation` | Loom 1.19 已无该配置，统一用 `implementation` |

**下一步（按顺序）**

1. M3：注册 `onekey:sort` payload，打通「按键 → 服务端排序 → 回包提示」，单人与专用服务器各验一遍
2. M4：`ActionRegistry` + 设置界面骨架（先放 4 个开关，验证读写成环）
3. M5：容器扫描与堆叠算法（重点做物品安全回归）
4. M6 → M7 → M8

**待确认的产品决策**（不阻塞编码，可在 M4 前定）

- 默认整理键 `R` 与 IPN 冲突时的取舍：改默认 `G`，还是首次启动弹窗让玩家选？
- 快捷栏是否参与排序：默认「不参与」，是否需要开关？
- 锁定槽位是否跨存档共享：按容器类型全局记录（当前设计），还是每个存档单独存？
