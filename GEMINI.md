# 项目协作与行为准则 (Workspace Rules)

## 1. 默认调用 Gemini API 准则
- **默认机制**：除非用户在指令中明确提出**“使用额度”**，否则后续所有需借助模型能力进行深层生成、分析、调试、方案起草的工作，一律默认调用用户提供的 Gemini API 执行。
- **API 配置与调用工具**：
  - API Key 已保存在项目根目录的本地环境配置文件 `.env`（已加入 `.gitignore`，严禁提交到 Git）。
  - 项目内置了零依赖且支持管道/上下文文件的调用脚本：`scripts/gemini.sh` 及 `scripts/gemini_api.py`。
  - 默认模型为 `gemini-3.8-flash`（可根据需要指定 `-m gemini-4-argon` 等）。
  - 示例：`./scripts/gemini.sh "你的分析提示词"` 或 `cat log.txt | ./scripts/gemini.sh "分析日志"`。

## 2. Git 规范与多智能体防冲突规则
- **严禁全量暂存**：严禁执行 `git add .`、`git add -A` 或 `git commit -a`。
- **精确暂存流程**：
  1. 任何提交前，必须先执行 `git status` 确认当前工作区的全部变动与文件归属。
  2. 仅对本次任务明确修改或创建的文件执行 `git add <具体文件路径>`。
  3. 确保不将其他智能体或用户正在编辑的未完成代码误扫入 commit。
- **安全与凭据保护**：
  - 严禁将 `.env`、`secrets.properties`、Token、API Key、SSH 私钥等任何敏感凭证提交至仓库。
