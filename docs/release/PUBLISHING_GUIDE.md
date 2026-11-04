# OneKey 模组全平台发布操作指南 (Publishing Guide)

本指南汇总了将 **OneKey v1.0.3** 发布至 **GitHub Releases**、**Modrinth** 与 **CurseForge** 的现成物料与填报步骤。

---

## 一、GitHub Releases（v1.0.3）

- **发布链接**：[https://github.com/Roland114514/OneKey/releases/tag/v1.0.3](https://github.com/Roland114514/OneKey/releases/tag/v1.0.3)
- **已挂载产物**：
  - `onekey-fabric-1.0.3.jar` (114 KB)
  - `onekey-fabric-1.0.3-sources.jar`
  - `onekey-neoforge-1.0.3.jar` (112 KB)
  - `onekey-neoforge-1.0.3-sources.jar`
- **校验和**：
  - Fabric: `abefc2a21f995defb100246cea33030956957a744c3959ece2b9d7c73e9fbde9`
  - NeoForge: `e775ca9a5a89a4365447a23c4d26c836befc6a1e8a5b6e654d936067b1464c6c`

---

## 二、Modrinth 发布指引 (新手创建仅需 2 分钟)

由于 Modrinth 的初始项目必须由作者首次登录并提交审核，请按照以下直接参数复制填报：

1. **创建入口**：访问 [https://modrinth.com/create/mod](https://modrinth.com/create/mod) 并登录您的账号；
2. **基础信息填报**：
   - **Name (模组名称)**：`OneKey · 操作优化`
   - **Slug (自定义 URL)**：`onekey`（如已被占用可用 `onekey-mod`）
   - **Summary (简短一句话描述)**：
     > 轻量级、零第三方依赖的 Minecraft 26.3 操作优化模组。一键整理、快速堆叠、一键存入、统一热键、槽位锁定与鼠标手势。
   - **Category (分类标签)**：勾选 `Utility`、`Storage`、`Management`
   - **Icon (项目图标)**：上传 [`docs/release/icon.png`](icon.png)（128x128 像素标准图标）
   - **License (开源协议)**：选择 `MIT`
   - **Source Repo (源码仓库)**：`https://github.com/Roland114514/OneKey`
   - **Issues / Bug Tracker**：`https://github.com/Roland114514/OneKey/issues`
3. **详细正文介绍**：
   - 直接将 [`docs/release/MODRINTH.md`](MODRINTH.md) 中的 Markdown 内容**全选复制**并粘贴至 Description 输入框。
4. **提交审核与上传文件**：
   - 点击 **Submit** 完成项目创建（Modrinth 审核极快）；
   - 在项目页的 **Versions** 标签点击 **Create version**：
     - 上传 [`docs/release/onekey-fabric-1.0.3.jar`](onekey-fabric-1.0.3.jar)，勾选 `Fabric`、MC 版本 `26.3`、Release Channel `Release`；
     - 上传 [`docs/release/onekey-neoforge-1.0.3.jar`](onekey-neoforge-1.0.3.jar)，勾选 `NeoForge`、MC 版本 `26.3`、Release Channel `Release`。

---

## 三、CurseForge 发布指引 (新手创建填报)

CurseForge 同样支持直接提交审核：

1. **创建入口**：访问 [CurseForge Author Dashboard](https://authors.curseforge.com/dashboard/projects/create) 或 [Minecraft Mods Submit](https://www.curseforge.com/minecraft/mc-mods)；
2. **基础信息填报**：
   - **Project Name**：`OneKey - Action & Inventory Optimization`
   - **Project Summary**：
     > Ultra-lightweight, zero-runtime-dependency inventory optimization mod for Minecraft 26.3 (Fabric & NeoForge). Sort, Quick Stack, Slot Locks & Gestures.
   - **Primary Category**：`Storage` 或 `Quality of Life` / `Utility`
   - **Project Description**：复制 [`docs/release/CURSEFORGE.md`](CURSEFORGE.md) 内容；
   - **Project Avatar**：上传 [`docs/release/icon.png`](icon.png)；
   - **Source Code URL**：`https://github.com/Roland114514/OneKey`
   - **Issues URL**：`https://github.com/Roland114514/OneKey/issues`
3. **文件上传 (Files)**：
   - 审核通过后在 **Files** 页面上传：
     - `onekey-fabric-1.0.3.jar`（勾选 Java 25、Minecraft 26.3、Fabric）
     - `onekey-neoforge-1.0.3.jar`（勾选 Java 25、Minecraft 26.3、NeoForge）
