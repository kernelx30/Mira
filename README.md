# Mira

<div align="center">
  <img src="docs/assets/mira-icon.svg" width="96" height="96" alt="Mira 图标"><br>
  <strong>面向 Android 的本地优先 AI 伴侣</strong><br>
  聊天、记得、陪伴、可控，同时保留完整 Agent 能力
</div>

<div align="center">
  <a href="README(E).md">English</a> ·
  <a href="https://kernelx30.github.io/Mira/">在线教程</a> ·
  <a href="https://kernelx30.github.io/Mira/plugin.html">插件开发</a> ·
  <a href="PRIVACY.md">隐私说明</a> ·
  <a href="SECURITY.md">安全政策</a> ·
  <a href="https://github.com/kernelx30/Mira/releases">下载</a> ·
  <a href="https://github.com/kernelx30/Mira/issues">问题反馈</a> ·
  <a href="https://github.com/AAswordman/Operit">上游 Operit</a>
</div>

<div align="center">
  <img src="https://img.shields.io/github/license/kernelx30/Mira" alt="License">
  <img src="https://img.shields.io/github/last-commit/kernelx30/Mira" alt="Last commit">
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF" alt="Kotlin and Compose">
  <img src="https://img.shields.io/badge/status-0.1.x%20pre--release-1565C0" alt="Pre-release">
</div>

> [!IMPORTANT]
> Mira 当前处于 `0.1.x` 发布候选阶段。仓库 Release 中由固定签名发布的 APK 才是公开版本；聊天群、网盘和第三方站点里的改包不属于项目发布物。

## Mira 是什么

Mira 是基于 [Operit](https://github.com/AAswordman/Operit) 深度改造的 Android AI 伴侣。它继承 Operit 的模型接入、工具调用、本地运行环境与扩展生态，但重新组织了产品主线：

```text
聊天消息
  -> 会话与章节
  -> 记忆提案、证据与关系
  -> 下一轮上下文
  -> 主动消息、提醒与语音陪伴
```

日常使用围绕四个词：

```text
聊天 -> 记得 -> 陪伴 -> 可控
```

应用 ID 是 `com.ai.assistance.mira`，可以与上游 Operit 分开安装。Mira 的源码、Issue、Release、更新检查和静态插件市场均指向 `kernelx30/Mira`。

## Mira 与 Operit 有什么不同

这不是“给 Operit 换个名字”。两者共享大量底层能力，但面向的第一使用路径不同。

| 维度 | Operit 的基础 | Mira 的重构方向 |
|---|---|---|
| 产品入口 | AI Agent、工具、工作区与自动化 | 先进入角色聊天，工具按任务出现 |
| 信息架构 | 功能模块完整、偏工具台 | 聊天页保持单一主线，复杂能力收进高级功能 |
| 会话 | 完整模型与 Tool Call 链路 | 增加会话竞态保护、请求恢复、历史全文搜索和沉浸式多气泡回复 |
| 记忆 | 原有记忆、知识库和图谱基础 | 独立的伴侣记忆库，区分用户、角色、关系、会话，并保留证据、版本和冲突历史 |
| 语音 | 多供应商 TTS/STT 与语音能力 | 增加情绪导演、分段队列、朗读进度、新消息打断和文本先于语音显示 |
| 主动性 | 工作流、定时任务与后台能力 | 结合最近会话、约定和角色关系生成主动消息，并提供本地调度与补偿 |
| 悬浮交互 | 桌宠和完整悬浮工作台能力 | 小气泡与紧凑快速回复卡片，完整配置仍回主应用 |
| 插件生态 | Operit 市场、Skill、MCP、ToolPkg | Mira 静态市场入口与 MiraForge 新发布，同时兼容既有 OperitForge 资源 |
| 品牌与更新 | Operit 包名、仓库和发布通道 | 独立应用 ID、Mira 图标、Mira GitHub Releases 更新源 |

Mira 会继续跟踪 Operit 上游的可复用修复，也会保留协议兼容。兼容不等于混用品牌：Mira 的产品设计、版本和发布节奏独立维护。

## 真机界面

<div align="center">
  <img src="docs/assets/guide/beginner/18-first-deepseek-reply.png" width="42%" alt="Mira 聊天界面">
  <img src="docs/assets/README_examples/mira/settings.png" width="42%" alt="Mira 设置界面">
</div>

## 已实现的核心能力

### 1. 聊天与会话

- 角色化聊天、流式输出、上下文总结与会话级模型配置
- 最近、置顶、归档、导出，以及标题和消息内容搜索
- 沉浸式回复可按内容生成一条或多条气泡，不固定成一句话
- 工具调用轨迹、执行状态和原始结果仍可查看
- 请求超时、空响应、取消、应用切换和会话切换有独立恢复路径
- 手机使用全屏单栏聊天；折叠屏、平板和宽屏使用受限正文宽度与自适应侧栏

### 2. 角色、关系与群聊

- 每个角色拥有独立头像、人设、开场白、语音补充设定和能力授权
- 用户名称、用户头像等稳定资料可作为全局资料复用
- 角色记忆和双方关系默认按角色隔离，避免不同角色自动共享共同经历
- 群聊由群组规划能力决定发言角色和顺序，角色仍保留各自设定
- 支持兼容角色卡导入、标签管理和新角色独立会话

### 3. 语音与陪伴

- 豆包、MiniMax、MiMo、OpenAI、Deepgram、SiliconFlow、HTTP TTS/STT 等可配置接入
- `Expressive TTS Director` 将文本拆成有限数量的情绪片段，再按供应商能力映射情绪、风格、语速和音高
- 供应商缺少情绪字段时，自动退化为风格指令、韵律或普通朗读
- 流式文本先发布到聊天，再排队播放语音，减少“声音播完文字才出现”
- 朗读状态、当前片段、暂停、停止和新消息打断共用一套播放状态
- 主动提醒、通知、语音通话、陪伴气泡和快速回复卡片

### 4. Agent 与高级能力

- 模型默认可以在任务需要时调用工具，不额外设置“仅聊天”模式
- Skill、MCP、脚本、ToolPkg、工作流和知识库
- 文件、终端、SSH/SFTP、工作区、网页与深度搜索
- Shizuku、无障碍、Root、AutoGLM 和虚拟屏幕等设备协助
- MNN、llama.cpp/GGUF 等本地模型模块
- Tasker、广播、本地 HTTP 等外部调用入口

## 记忆系统

Mira 把聊天记录与长期记忆分开。聊天记录负责还原发生过什么；长期记忆只保存以后值得继续使用的事实、事件、偏好、边界、约定和关系状态。

### 四种记忆范围

| 范围 | 内容 | 默认可见性 |
|---|---|---|
| `USER` | 用户稳定身份、全局偏好和通用边界 | 可供所有角色使用 |
| `COMPANION` | 角色自身设定、角色私有认知 | 仅该角色 |
| `RELATIONSHIP` | 用户与某角色的称呼、经历、约定和关系状态 | 仅这一对关系 |
| `CONVERSATION` | 当前会话摘要、章节与未完成话题 | 仅当前会话参与者 |

底层数据模型还支持针对单条记忆授予或撤销其他角色的 `READ` 权限。角色私有记忆不会因为换角色就自动串过去；全局用户事实也会与角色私有经历分开标记。

### 每轮处理链路

```text
回复完成
  -> 原始消息先持久化
  -> 本轮进入记忆候选队列
  -> 记忆功能模型提出 CREATE / UPDATE / SUPERSEDE / LINK / IGNORE
  -> 本地规则校验主体、范围、证据、重复、冲突和敏感内容
  -> 写入 Room，并更新全文索引和关系边
  -> 下一轮回复前按角色、会话、相关性和 Token 预算召回
```

每个完成的对话轮次都会进入候选判断，但模型可以返回 `IGNORE`。这表示“每轮都检查”，不表示“每句话都永久记住”。

### 不静默覆盖历史

- 记忆状态包含 `ACTIVE`、`ARCHIVED`、`SUPERSEDED` 和 `DELETED`
- 新事实替代旧事实时，新记录通过 `SUPERSEDES` 关系指向旧记录
- 默认回答只使用当前有效事实，历史版本仍可追溯
- 证据保存来源会话、消息、说话人、原始引用和时间
- 用户可新增、确认、纠正、归档或删除；删除会同步清理证据、关系边和索引引用
- FTS4 全文索引失效时，仍有结构化与词法检索作为退路

记忆数据库当前由 Android 应用沙箱保护。它不是云端同步服务，也不等同于端到端加密保险箱；导出的备份应按敏感文件处理。

## 插件市场与兼容层

Mira 默认读取自己的静态市场入口：

```text
https://kernelx30.github.io/Mira/market/v2/
```

市场保留 Operit 兼容数据，已有插件仍从作者自己的仓库或 Release 下载。新发布优先使用 `MiraForge`；已经安装或已经发布在 `OperitForge` 的条目继续沿原来源更新，避免兼容层硬切断。

以下内部标识暂时保留：

- `com.ai.assistance.operit` Kotlin/Java 命名空间
- `.operit/market.json` 安装标记
- 既有 JS Bridge、ToolPkg、Skill 和 MCP 协议字段

这些标识用于兼容现有生态。贸然全局改名会直接破坏插件、序列化数据、数据库迁移和第三方集成。

## 隐私与安全边界

“本地优先”指资料默认由用户设备持有，不等于启用云端服务后数据仍完全停留在手机。

| 功能 | 数据去向 |
|---|---|
| 聊天记录、角色卡、关系与记忆 | Android 应用私有目录；用户主动导出时写入所选位置 |
| 云端大模型 | 当前消息、系统提示、选中的历史、相关记忆、附件和工具结果发送到用户配置的 Endpoint |
| 云端 TTS | 待朗读文本、音色和风格参数发送给语音服务商 |
| 云端 STT | 录音片段和识别参数发送给语音识别服务商 |
| 在线搜索、MCP、Skill、ToolPkg | 查询词、工具参数、选定文件或任务上下文可能发送到对应端点 |
| 本地模型、本地 TTS/STT | 推理可留在设备；它们调用的联网工具仍遵循各自配置 |
| Mira 静态市场 | 读取公开插件元数据；插件文件来自对应作者仓库 |

安全要点：

- Mira 不使用自建聊天中转服务器；云端请求直达用户配置的模型或语音 Endpoint
- API Key 保存在应用私有数据中，但完整备份和模型配置导出可能包含 Key、Header 与 Endpoint
- 当前导出备份不应被当作自动加密文件；离开应用沙箱后由用户负责存放和传输
- 普通文字聊天只需要网络；麦克风、位置、悬浮窗、无障碍、Shizuku 和 Root 均按功能授权
- 插件会在已授予的文件、网络、终端或设备权限范围内运行，安装前应检查源码、作者、更新地址和网络端点
- 建议使用独立、可撤销、有限额的 API Key，并在服务商后台设置预算和告警
- 提交 Issue 或公开截图前，检查 Key、Token、Cookie、私人聊天、位置、Wi-Fi/BSSID、文件路径和设备标识

完整说明见 [PRIVACY.md](PRIVACY.md) 与 [SECURITY.md](SECURITY.md)。

## 五分钟开始使用

1. 从 [Releases](https://github.com/kernelx30/Mira/releases) 下载项目签名的 APK。
2. 打开 Mira，只授予通知和当前要用的麦克风等基础权限。
3. 进入“设置 → 模型与 API”，创建一个对话模型配置并执行“测试连接”。
4. 返回聊天页发送一句短消息，确认文字流式输出正常。
5. 再配置角色、记忆和 TTS；Shizuku、终端、MCP 等高级能力最后开启。

完整图文流程：[Mira 新手完整教程](https://kernelx30.github.io/Mira/)

插件、ToolPkg、调试与 MiraForge 发布：[Mira 插件开发文档](https://kernelx30.github.io/Mira/plugin.html)

## 从 Operit 迁移

- 两个应用使用不同应用 ID，可同时安装
- 聊天备份页面提供 Operit 聊天备份识别和导入入口
- 角色卡、Skill、MCP、脚本和 ToolPkg 继续使用兼容格式
- 导入前先在 Operit 生成备份；导入后随机核对会话、角色和记忆
- API Key 与高权限授权建议在 Mira 中重新配置，不把旧应用私有目录当成迁移接口

## 构建

### 环境

- Android Studio / JDK 17
- Android SDK 36
- Android NDK 与 CMake
- Git submodule

### 获取源码

```bash
git clone --recursive https://github.com/kernelx30/Mira.git
cd Mira
```

已有仓库缺少子模块时：

```bash
git submodule update --init --recursive
```

### 本地配置

参考 `local.properties.example` 配置本机 Android SDK、GitHub OAuth 和 release 签名字段。真实 API Key、OAuth Secret、keystore 与密码留在本机，不提交到 Git。

### Debug APK

Windows：

```powershell
.\gradlew.bat :app:assembleDebug
```

Linux/macOS：

```bash
./gradlew :app:assembleDebug
```

输出：

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release 构建、固定签名、版本号、标签和更新验收见 [Mira 发布与更新流程](docs/Mira_Release_Guide_CN.md)。

## 仓库与上游

```text
origin    https://github.com/kernelx30/Mira.git
upstream  https://github.com/AAswordman/Operit.git
```

`origin` 用于 Mira 开发与发布，`upstream` 用于跟踪 Operit 的可复用修复。同步上游时先建立独立分支，重点复核包名、数据库迁移、品牌资源、更新源、记忆模型和插件兼容层。

## 当前状态与已知限制

- 当前版本线为 `0.1.x`，数据库和交互仍会继续收敛
- 角色间单条记忆授权的数据层已存在，面向普通用户的授权管理界面仍在完善
- 主动消息受 Android 省电、自启动和后台限制影响，不作为系统闹钟替代品
- 不同模型和语音服务支持的 Tool Call、流式输出、情绪和音色能力不同
- APK 包含多个本地模型与 native 模块，安装包体积较大
- Operit 兼容标识仍会出现在内部路径、日志或协议中，这属于兼容层，不代表更新源指向上游

## 开源与致谢

Mira 基于 Operit 开发，并保留 LGPLv3 许可证与原始版权信息。感谢 Operit 项目及其贡献者提供的 Agent、工具、插件和本地运行基础。

- Mira：[kernelx30/Mira](https://github.com/kernelx30/Mira)
- 上游：[AAswordman/Operit](https://github.com/AAswordman/Operit)
- 许可证：[LICENSE](LICENSE)
