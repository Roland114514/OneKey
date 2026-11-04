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
| F11 | 鼠标手势进阶（学习 [MouseTweaks](https://github.com/YaLTeR/MouseTweaks)） | Shift+左键划选转移（LMB Sweep）、RMB 重复滑放、滚轮反转开关、纯客户端点击模拟降级 | v1.1 |

---

## 4. 里程碑总表

| 里程碑 | 周期 | 交付物 | 验收标准 | 状态 |
| --- | --- | --- | --- | --- |
| **M0 环境与工具链** | D1（11-03） | JDK 25、Gradle wrapper、`scripts/with-jdk25.sh`、`.gitignore` | `./gradlew --version` 输出 Gradle 9.8.2 / JVM 25；缓存写入项目内 `.gradle-home/` | ✅ 完成 |
| **M1 项目骨架** | D1（11-03） | `settings.gradle`、`gradle.properties`、`fabric/build.gradle`、`fabric.mod.json`、双 mixin 配置、中英文语言文件、图标、README/LICENSE | `./gradlew build` 通过；产物 jar 可被 Fabric 识别（`fabric.mod.json` 校验通过） | ✅ 完成 |
| **M2 内核：排序 + 配置 + 协议** | D1（11-03） | `common` 下 sort/config/action/net 包；JUnit 5 单测 13 例 | 全部单测通过（排序双射、锁定槽位、空格后置、配置往返/缺省/null 归一） | ✅ 完成 |
| **M3 一键整理落地** | D2–D4（11-04 ~ 11-06） | 客户端 `KeyMapping` → `SortRequest` payload；服务端 `ServerPlayNetworking` 收包、排序、回包；`SortResult` 提示 | 单人/专用服务器均能整理；快捷栏严格不参与；锁定槽位按存档独立持久化；连续按 250 ms 冷却；未装服务端时不崩、提示「服务端未安装」 | ✅ 完成 |
| **M4 热键框架 + 设置界面** | D4–D6（11-06 ~ 11-08） | `ActionRegistry`（动作 ↔ 热键 ↔ 翻译键）；`Screen` 基类的设置界面（排序规则开关、堆叠半径、按键重绑入口）；`Alt+左键` 槽位锁定交互与高亮渲染 | 4 个热键全部接入统一动作注册；设置改动即时生效；界面可用键盘导航、支持中英文；槽位锁定前后端同步与高亮 | ✅ 完成 |
| **M5 快速堆叠 / 存入** | D6–D9（11-08 ~ 11-11） | 服务端高性能区块扫描附近容器（默认 6 格）、两阶段贪心合并与存入；`QuickStackRequest/Result` 网络接线；分类静态缓存集成 | 半径内同类物品正确合并；空槽按 stashMode 智能填充；快捷栏与锁定槽位严格受保护；总物品绝对守恒 | ✅ 完成 |
| **M6 客户端手势 + 兼容** | D9–D11（11-11 ~ 11-13） | 乐观 UI 预测、Shift 双击批量转移、滚轮单件进出微调；与 IPN/Inventory Sorting/Mouse Tweaks/Item Scroller 冲突检测与提示 | 0ms 整理响应；滚轮与双击手势严格保护快捷栏与锁定槽位；进入世界检测并提示共存模组 | ✅ 完成 |
| **M7 NeoForge 适配** | D11–D14（11-13 ~ 11-16） | `neoforge/` 模块（ModDevGradle）、事件与网络适配层 | `./gradlew :neoforge:build` 通过；同一套 `common` 内核；功能对齐 Fabric 端 | ✅ 完成 |
| **M8 发布** | D14–D15（11-16 ~ 11-17） | Modrinth/CurseForge 页面、GitHub Release、变更日志、双加载器 jar | 三处同版本可下载；干净客户端 + 原版服务器实测通过；双端 v1.0.0 发布包就绪 | ✅ 完成 |

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

| # | 任务 | 技术要点 | 状态 |
| --- | --- | --- | --- |
| 3.1 | 定义 payload | `CustomPacketPayload` + `PayloadTypeRegistry`，通道 `onekey:sort_request` / `onekey:sort_result`；字段 `syncId`、`mode` | ✅ |
| 3.2 | 客户端发请求 | 监听 R 键，检测 `canSend`，仅在 `containerMenu.containerId == syncId` 时发送，防错位 | ✅ |
| 3.3 | 服务端校验 | 校验玩家打开的容器与 `syncId` 一致、`cooldownMs` 内拒绝重复请求 | ✅ |
| 3.4 | 服务端执行排序 | 遍历 `slots`，底部 9 格快捷栏严格不参与整理，锁定槽位按存档独立持久化读取，调用 `Sorter.permutation`，用 `slot.set` 写回 | ✅ |
| 3.5 | 回包与提示 | 发送 `SortResult(success, moved)`，客户端 `sendOverlayMessage` 提示「已整理 N 格」 | ✅ |
| 3.6 | 降级路径 | 服务端未装：`canSend` 返回 false → 本地 overlay 提示「服务器未安装 OneKey，无法整理」，不强改本地背包 | ✅ |
| 3.7 | 测试 | 14 例单测全绿（含快捷栏隔离测试），服务端加载锁定槽位与网络包正常 | ✅ |

### M4 热键框架 + 设置界面（详细任务）

| # | 任务 | 技术要点 | 状态 |
| --- | --- | --- | --- |
| 4.1 | `ActionRegistry` | 动作 ↔ 默认键 ↔ 翻译键集中注册，KeyMapping 由此生成 | ✅ |
| 4.2 | 配置热键落地 | 启动时由 `ActionRegistry` 批量注册 KeyMapping，支持 K 键一键呼出设置界面 | ✅ |
| 4.3 | 设置界面 | 继承 `Screen`，原版控件（`CycleButton` / `Button`），涵盖排序开关、半径、模式与按键入口 | ✅ |
| 4.4 | 持久化与生效 | 界面关闭与更改即调用 `OneKeyFabric.saveConfig()` 写回 `config/onekey.json` | ✅ |
| 4.5 | 槽位锁定交互与渲染 | `Alt + 左键` 监听切换，双向网络 Payload 同步存档；`AbstractContainerScreenMixin` 渲染琥珀金高亮边框 | ✅ |
| 4.6 | 测试 | 单测扩充至 19 例，构建与 Clean Jar 打包通过，中英文键名齐全 | ✅ |

### M5 快速堆叠 / 存入（详细任务）

| # | 任务 | 技术要点 | 状态 |
| --- | --- | --- | --- |
| 5.1 | 容器扫描 | 以玩家为中心 `radius` 区块内 BlockEntity 智能检索，获取 Chest/Barrel/ShulkerBox 等，避免暴力遍历 | ✅ |
| 5.2 | 堆叠算法 | 两阶段：先填未满堆叠（同物品且 `count < maxCount`），再找空槽；受黑白名单与锁定槽位约束 | ✅ |
| 5.3 | 存入算法 | `stashMode=true`（B 键）存全部可存；`false`（V 键）仅补容器里已存在的物品类型 | ✅ |
| 5.4 | 物品安全 | 快捷栏 0..8 永久保护不存入，锁定槽位不存入；操作全流程总物品数严格守恒 | ✅ |
| 5.5 | 回包与统计 | `QuickStackResult(moved)` → 客户端提示「已快速堆叠 N 件物品」并播放容器操作音效 | ✅ |
| 5.6 | 性能优化 | 接入 `ItemConversion` 类别与 ID 静态缓存，减少临时对象垃圾；区块级 BlockEntity 检索压低扫描耗时 | ✅ |
| 5.7 | 测试 | 28 例单测全部通过（含两阶段堆叠、空槽填充、快捷栏/锁定保护、物品守恒定律、黑白名单测试） | ✅ |

### M6 手势 + 兼容（详细任务）

| # | 任务 | 产出 / 技术要点 | 状态 |
| --- | --- | --- | --- |
| 6.1 | 乐观 UI 预测（0ms 响应） | 客户端本地先行置换槽位并播放音效，服务端校验后平滑确认 | ✅ |
| 6.2 | Shift + 双击批量同类转移 | 双击槽位将同侧所有相同未锁定物品快速移入对侧容器（快捷栏与锁定槽位严格免疫） | ✅ |
| 6.3 | 鼠标滚轮单件物品进出微调 | 悬浮槽位上下滚动滚轮，以 1 件为单位在容器与主背包间双向微调转移 | ✅ |
| 6.4 | 模组冲突检测与友好提示 | 进入世界自动检测 IPN / Inventory Sorter / Mouse Tweaks / Item Scroller，一次性聊天栏友好提示 | ✅ |
| 6.5 | 交互边界加固与防抖 | 滚轮 40ms 防抖、白名单存储容器过滤、光标持物保护、双击 300ms 窗口与槽位匹配 | ✅ |
| 6.6 | 验证与回归测试 | 29/29 项内核单测全部通过；`clean build` 零警告通过 | ✅ |

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
| `quickStack.whitelist` | list | `[]` | 允许的容器方块 id；空 = 不限制 |
| `quickStack.blacklist` | list | `[]` | 禁止的容器方块 id（优先于白名单） |
| `quickStack.itemWhitelist` | list | `[]` | 参与堆叠的物品 id；空 = 不限制 |
| `quickStack.itemBlacklist` | list | `[]` | 排除的物品 id（优先于物品白名单） |
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

- [x] `./gradlew build` 绿；`./gradlew test` 40 例绿（Fabric & NeoForge 双端各 40 例，合计 80 例全绿）
- [x] `fabric.mod.json` 与 `neoforge.mods.toml` 依赖版本与实测一致（`minecraft ~26.3`、`java >=25`）
- [x] jar 内含 `LICENSE_onekey`、图标、中英文语言文件、Mixin 配置
- [x] Modrinth 发布物料：`docs/release/MODRINTH.md` 包含标题/简介/特性/双端版本表
- [x] CurseForge 发布物料：`docs/release/CURSEFORGE.md` 同步就绪
- [x] GitHub Release：`docs/release/GITHUB_RELEASE.md`，带 SHA-256 校验和与双端 jar
- [x] CHANGELOG：`CHANGELOG.md` 完整记录 F1–F6 + F10 列表与注意事项
- [x] 产物归档：`fabric/build/libs/onekey-fabric-1.0.2.jar` (110 KB) 与 `neoforge/build/libs/onekey-neoforge-1.0.2.jar` (108 KB)

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
| `client.screen` | `client.gui.screen()`（26.3 将屏幕引用迁移至 Gui 实例下） |
| `player.displayClientMessage(msg, true)` | `player.sendOverlayMessage(msg)`（ActionBar 提示在 26.3 改为专用方法） |

**产品决策确认记录（2026-11-03 用户拍板）**

1. **整理热键**：默认整理键继续保持为 `R`。
2. **快捷栏规则**：底部 9 格快捷栏（0..8）严格**不参与**背包整理，保证手持工具和武器不被冲乱。
3. **锁定槽位作用域**：锁定槽位按**每个世界存档单独持久化记住，互相独立**（保存在 `<worldSaveDir>/onekey/locked_slots.json`）。

**下一步（按顺序）**

1. 推进 v1.1 迭代（参考第 13 节 MouseTweaks 手势增强吸收、自然滚动反转与纯净服降级模式）

---

## 13. v1.1 迭代规划（MouseTweaks 手势增强吸收与深度演进）

> 参考项目：[MouseTweaks (by YaLTeR)](https://github.com/YaLTeR/MouseTweaks)
> 定位原则：在完成 v1.0 稳定发布（M8）后启动；在吸收其丝滑手势的同时，保持 OneKey 独有的「底部快捷栏绝对保护」、「槽位加锁安全屏障」和「防作弊物品守恒」。

### 拟吸收与改进的 4 大核心特性

1. **Shift + 左键拖拽扫掠连续转移（LMB Drag Sweep）**：
   - 机制：按住 Shift，按住左键在背包/箱子上滑过，经过的所有槽位按顺序自动执行快速移动（Shift-Click）到对侧容器；
   - 增强：**严格跳过处于锁定状态的槽位**，且**严格跳过底部 0..8 快捷栏**，彻底解决 MouseTweaks 会误把随身剑/工具滑入箱子的痛点。
2. **RMB 划动重复投放（RMB Drag Repeated Placement）**：
   - 机制：合成台/背包右键拖拽时，允许鼠标多次滑过同一个槽位继续重复投入 1 件物品（如绕圈 8 次即每个槽位各放 8 件，极大便利大批量合成）。
3. **滚轮体验深度打磨（Wheel Tweaks Plus）**：
   - 自然滚动反转：配置增加 `sort.invertWheel`（默认 false），设置界面提供开关，完美适配 macOS 触控板与习惯反转的玩家；
   - 空槽交互微调：手持物品悬停空槽时，滚轮可单件连续分发或收回。
4. **客户端降级模拟模式（Vanilla Click Fallback）**：
   - 机制：当进入未安装 OneKey 服务端的第三方/原版服务器时，手势操作自动降级为调用原版 `gameMode.handleInventoryMouseClick` 发送原版容器点击包，保证在任何纯净服务器上依然能享受完整手势增强。
5. **B 键快速存入精准控制重构（准星视线锁定优先 + 智能分类防污染）**：
   - 痛点：目前采用以玩家为中心的纯几何距离扫描，多个并排箱子时无法控制存入哪一个，距离最近的箱子会成为黑洞吸走所有异物，打乱玩家分类；
   - 准星视线锁定（Crosshair Raycast）：准星对准有效容器时目标唯一锁定为该容器，实现“看哪存哪”；未对准容器时退化为范围广播；
   - 智能分类防污染（Smart Matching）：范围广播存入时，严禁向空槽存入该容器原本没有的陌生物品，分类箱受绝对保护；
   - 容器界面强绑定：在打开某容器 GUI 时按 B，目标强制锁定为当前打开的容器。

