# 👨‍💻 开源共创指南

欢迎参与 Mira 开源项目。Mira 基于 Operit 持续开发，并维护自己的产品、发布和插件生态。

## 脚本与插件开发者

### 📜 脚本开发

Mira 支持通过 TypeScript/JavaScript 脚本扩展角色与 Agent 能力。完整指南请参考 [脚本开发指南 (SCRIPT_DEV_GUIDE.md)](../../SCRIPT_DEV_GUIDE.md)。

### 🔌 MCP 插件开发

你可以开发自己的 MCP 服务来扩展网页、图像、数据库和外部系统能力，再从 Mira 导入仓库、ZIP 或连接配置。

## Mira 本体开发者

参与 Mira 本体开发，请遵循以下精简指南。

### 🛠️ 环境搭建

在开始开发之前，请参考 [完整编译指南 (BUILDING.md)](./BUILDING.md) 搭建 Android 开发环境。

### 🚀 开发前必读

1.  **先沟通**: 在 [Mira Issues](https://github.com/kernelx30/Mira/issues) 提出想法或认领任务，避免重复开发。
2.  **研究代码**: 动手前，请**深入阅读**相关模块的现有代码，理解项目的设计模式和架构。
3.  **保持兼容**: 新功能必须**向前兼容**，不能破坏现有用户体验或数据结构。
4.  **遵循结构**: 将新文件放置在项目结构中合适的目录，保持代码库整洁。

### 🎨 代码风格

我们的代码风格...比较随性。欢迎你来帮忙统一！

- commit 信息非常"创意丰富"
- 注释语言混搭风，中英文随心切换
- 代码风格多元化

### 🔄 提交流程

为了顺利合入你的代码，请严格遵循以下流程：

1.  **准备工作**:
    - Fork 本仓库并 Clone 到本地。
    - 添加 Mira 主仓库: `git remote add upstream https://github.com/kernelx30/Mira.git`
    - 需要跟踪 Operit 时另加: `git remote add operit-upstream https://github.com/AAswordman/Operit.git`

2.  **开始开发**:
    - 同步最新的 `main`，再建立功能分支。
      ```bash
      git fetch upstream
      git checkout main
      git merge upstream/main
      ```
    - 从 `main` 创建你的功能分支。
      ```bash
      git checkout -b feature/your-feature-name
      ```

3.  **提交代码**:
    - 完成开发后，**同步 `main` 分支的最新代码**。推荐使用 `rebase` 以保持历史记录清晰。
      ```bash
      git fetch upstream
      git rebase upstream/main # 或者 git merge upstream/main
      ```
    - 解决所有冲突后，推送到你的远程分支。
      ```bash
      # 如果 rebase 过，需要使用 --force
      git push origin feature/your-feature-name --force
      ```

4.  **创建 Pull Request**:
    - 打开 GitHub，向 `kernelx30/Mira` 的 `main` 分支创建 Pull Request。

### ⚠️ 重要提醒

- **先沟通，再开发**，避免重复工作。
- **所有 PR 必须基于最新 `main` 创建独立功能分支**。
- **提交 PR 前，请务必同步最新的 `main` 分支**，并解决所有冲突。
- 在 PR 中清晰说明你的改动。

---

我们期待你的贡献。每一次 PR、Issue 和讨论都会帮助 Mira 继续完善。
> **关于项目维护**: 项目的发展依赖社区的参与。感谢你的每一份贡献！

---

## 社区贡献与衍生项目指南

我们欢迎社区基于 Mira 或其上游兼容协议进行二次创作。为了保持项目透明和生态健康，建议所有衍生项目：

1.  **在公开代码托管平台上发布源代码。** 这样社区才能审查、学习并参与贡献。
2.  **在您的项目文档中明确致谢并链接回本项目。** 这有助于用户追溯代码来源，也是对我们工作的尊重和认可。

遵循这些建议将帮助我们共同构建一个更加开放、协作和安全的社区环境。 
