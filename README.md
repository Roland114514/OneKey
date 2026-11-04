# OneKey · 我的世界操作优化

面向 **Minecraft 26.3（Java 25）** 的轻量操作优化模组：一键整理背包、统一快捷键框架、快速堆叠与存入附近容器。

- 零第三方运行时依赖，不引入 UI 库，不与同类整理 mod 抢热键冲突逻辑
- 纯逻辑内核（排序 / 配置 / 协议）与加载器解耦，Fabric 先行，NeoForge 排期适配
- 只做操作优化，不改方块、不改数值，可用于原版服务器（服务端未装时自动降级）

> 详细的里程碑、排期与验收标准见 [PLAN.md](PLAN.md)；每天收工的工作日志（含交接信息与踩坑记录）见 [WORKLOG.md](WORKLOG.md)。

当前产物（2026-11-04 验证）：`bash scripts/with-jdk25.sh clean build` 通过、19 例单测全绿、开发客户端可进入标题界面并呼出设置菜单、开发服务端可正常启动并生成 `config/onekey.json`。

## 当前状态

| 阶段 | 内容 | 状态 |
| --- | --- | --- |
| M0 | 工具链（JDK 25 + Gradle 9.8.2 + Loom 1.19） | ✅ 完成 |
| M1 | 双源集项目骨架、资源与语言文件 | ✅ 完成 |
| M2 | 纯逻辑内核：排序、分类、配置、协议 DTO + 13 例单测 | ✅ 完成 |
| M3 | 一键整理（客户端请求 / 服务端执行） | ✅ 完成 |
| M4 | 统一热键框架 + 图形设置界面 + 槽位锁定交互 | ✅ 完成 |
| M5 | 快速堆叠 / 存入附近容器 | ⬜ 未开始 |
| M6 | 客户端点击模拟与容器手势增强 | ⬜ 未开始 |
| M7 | NeoForge 适配 | ⬜ 未开始 |
| M8 | 发布（Modrinth / CurseForge / GitHub Release） | ⬜ 未开始 |

## 快捷键（默认）

| 按键 | 功能 | 说明 |
| --- | --- | --- |
| `R` | 一键整理 | 鼠标悬停槽位/锁定槽位不参与排序 |
| `V` | 快速堆叠 | 把背包内物品堆叠进附近容器 |
| `B` | 存入附近容器 | 整叠存入，可按白/黑名单过滤 |
| `K` | OneKey 设置 | 图形界面修改热键与规则 |

所有按键均可在「选项 → 控制 → 按键绑定 → OneKey」中重绑，配置文件为 `config/onekey.json`。

## 从源码构建

需要 JDK 25（仓库自带脚本会复用 `.jdk/temurin-25`，并把 Gradle 缓存放到项目内 `.gradle-home/`）：

```bash
# 构建（含单元测试）
bash scripts/with-jdk25.sh build

# 启动带模组的客户端
bash scripts/with-jdk25.sh :fabric:runClient
```

产物：`fabric/build/libs/onekey-fabric-<version>.jar`。

## 目录结构

```
common/src/main/java/      纯逻辑内核（排序、分类、配置、协议），不引用 Minecraft
common/src/test/java/      内核单元测试（JUnit 5 + Gson）
fabric/src/main/java/      Fabric 入口与（后续）服务端逻辑
fabric/src/client/java/    Fabric 客户端入口、热键与界面
fabric/src/main/resources/ fabric.mod.json、mixin 配置、语言文件、图标
scripts/with-jdk25.sh      JDK 25 + 项目内 Gradle 缓存的构建包装脚本
```

## 许可证

[MIT](LICENSE)
