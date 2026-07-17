# Mira

<div align="center">
  <strong>面向 Android 的本地优先 AI 伴侣</strong><br>
  聊天、长期记忆、语音陪伴与完整 Agent 工具能力
</div>

<div align="center">
  <a href="README(E).md">English</a> ·
  <a href="https://github.com/kernelx30/Mira/releases">下载</a> ·
  <a href="https://github.com/kernelx30/Mira/issues">问题反馈</a> ·
  <a href="https://github.com/AAswordman/Operit">上游 Operit</a>
</div>

<div align="center">
  <img src="https://img.shields.io/github/license/kernelx30/Mira" alt="License">
  <img src="https://img.shields.io/github/last-commit/kernelx30/Mira" alt="Last commit">
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/Kotlin-Compose-7F52FF" alt="Kotlin and Compose">
</div>

## 项目定位

Mira 是基于 [Operit](https://github.com/AAswordman/Operit) 深度改造的 Android AI 伴侣。它不是给 Operit 换个名字，而是重新组织产品主线：

```text
聊天 -> 记得 -> 陪伴 -> 可控
```

Mira 将角色关系、长期记忆、语音交互和主动陪伴放在前台，同时保留 Operit 原有的工具调用、MCP、Skill、脚本、工具包、终端、工作流和设备协助能力。

应用 ID 为 `com.ai.assistance.mira`，可与上游 Operit 独立安装。

## 真机界面

<div align="center">
  <img src="docs/assets/README_examples/mira/chat-home.png" width="42%" alt="Mira 聊天界面">
  <img src="docs/assets/README_examples/mira/settings.png" width="42%" alt="Mira 设置界面">
</div>

## 当前能力

### 聊天

- 角色化聊天、会话历史、搜索、归档和导出
- 流式输出、沉浸式多气泡回复和消息状态恢复
- 会话级模型、思考模式、上下文窗口和记忆控制
- 附件、语音输入、自动朗读和实时语音相关能力
- 工具调用过程展示、失败恢复和会话切换保护

### 关系与记忆

- 用户、角色、关系和会话四种记忆范围
- 事实、偏好、事件、边界、约定和关系状态
- 记忆证据、版本、关联、纠正和手动保存
- 本地 Room/SQLite 存储与全文检索
- 回复前自动装配相关记忆，不依赖模型临时决定是否查询

### 语音与陪伴

- 多供应商 TTS/STT 配置
- 豆包、MiniMax、MiMo、OpenAI、HTTP TTS 等接口
- 情绪、语速、音高和分段播放指令
- 朗读进度高光、播放队列和新消息打断
- 主动提醒、通知、悬浮气泡和快速回复卡片

### Agent 与扩展生态

- 默认保留工具调用能力
- MCP、Skill、脚本和工具包安装
- Shizuku、终端、文件、工作区和设备协助
- MNN、llama.cpp 等本地模型模块
- 兼容 Operit 现有插件协议和市场资源

## 插件兼容说明

Mira 当前继续读取 Operit 兼容市场，插件仍从各自作者的仓库下载。这样已有 Skill、MCP、脚本和工具包可以继续使用。

为了避免破坏已安装插件，以下内部标识会暂时保留：

- `com.ai.assistance.operit` Kotlin/Java 命名空间
- `.operit/market.json` 安装标记
- 现有 JS Bridge、工具包和 Skill 协议字段

这些是兼容层，不代表 Mira 的项目仓库仍归 Operit。Mira 的源码、Issue、Release 和更新入口均指向本仓库。

## 界面原则

- 内容优先，减少工具台式堆叠
- 聊天页只保留当前交互需要的控制
- 手机、折叠屏、平板和桌面窗口共用自适应规则
- 统一处理状态栏、导航栏、键盘和悬浮层 Insets
- 默认使用克制的实色 Surface，复杂视觉效果作为可选项

## 构建

### 环境

- Android Studio
- JDK 17
- Android SDK 36
- Android NDK 与 CMake
- Git submodule

### 获取源码

```bash
git clone --recursive https://github.com/kernelx30/Mira.git
cd Mira
```

已有仓库缺少子模块时执行：

```bash
git submodule update --init --recursive
```

### 本地配置

复制 `local.properties.example` 中需要的字段到本机 `local.properties`。API Key、签名文件和 OAuth Secret 不应提交到 Git。

### 构建 Debug APK

Windows：

```powershell
.\gradlew.bat :app:assembleDebug
```

Linux/macOS：

```bash
./gradlew :app:assembleDebug
```

输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 仓库关系

推荐保留两个远程：

```text
origin    https://github.com/kernelx30/Mira.git
upstream  https://github.com/AAswordman/Operit.git
```

`origin` 用于 Mira 开发和发布，`upstream` 只用于跟踪 Operit 的可复用修复。

## 数据与隐私

- 聊天记录和伴侣记忆默认保存在设备本地
- 敏感配置存放在应用私有数据或本机 `local.properties`
- 公开提交前应检查模型配置导出、日志、备份和截图
- 第三方模型与语音服务的数据处理规则由对应服务商决定

## 项目状态

Mira 仍处于快速开发阶段。聊天、语音、记忆、悬浮窗和大屏适配正在持续收敛，数据库结构和交互细节可能继续调整。

发布版本见 [Releases](https://github.com/kernelx30/Mira/releases)，问题与建议提交到 [Issues](https://github.com/kernelx30/Mira/issues)。

## 开源与致谢

Mira 基于 Operit 开发，并保留其 LGPLv3 许可证和原始版权信息。感谢 Operit 项目及其贡献者提供的 Agent、工具、插件和本地运行基础。

- Mira：[kernelx30/Mira](https://github.com/kernelx30/Mira)
- 上游：[AAswordman/Operit](https://github.com/AAswordman/Operit)
- 许可证：[LICENSE](LICENSE)
