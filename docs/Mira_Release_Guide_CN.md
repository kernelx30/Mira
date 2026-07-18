# Mira 发布与更新流程

## 当前发布通道

- Mira 仓库：`https://github.com/kernelx30/Mira`
- Git remote `origin`：Mira 仓库
- Git remote `upstream`：`https://github.com/AAswordman/Operit.git`
- 应用内更新源：GitHub 仓库 `kernelx30/Mira` 的 Releases
- 当前初始版本：`versionCode = 1`，`versionName = "0.1.0"`

## 版本规则

版本配置位于 `app/build.gradle.kts`：

```kotlin
versionCode = 1
versionName = "0.1.0"
```

- `versionCode` 是 Android 覆盖安装使用的整数，每次发布必须递增，不能重复或降低。
- `versionName` 是用户看到的版本，预发布阶段使用 `0.x.y`。
- GitHub Release tag 使用 `v` 前缀，例如 `v0.1.0`、`v0.1.1`。
- Release tag 去掉 `v` 后，应与 `versionName` 完全一致。

示例：

| 发布 | versionCode | versionName | Release tag |
| --- | ---: | --- | --- |
| 首个内测版 | 1 | 0.1.0 | v0.1.0 |
| 修复版 | 2 | 0.1.1 | v0.1.1 |
| 功能版 | 3 | 0.2.0 | v0.2.0 |
| 正式版 | 4 | 1.0.0 | v1.0.0 |

## 固定发布签名

首次正式发布前生成并备份 Mira 专用 keystore：

```powershell
keytool -genkeypair -v `
  -keystore mira-release.jks `
  -alias mira `
  -keyalg RSA `
  -keysize 4096 `
  -validity 10000
```

在本机 `local.properties` 中配置：

```properties
RELEASE_STORE_FILE="C:/path/to/mira-release.jks"
RELEASE_STORE_PASSWORD="STORE_PASSWORD"
RELEASE_KEY_ALIAS="mira"
RELEASE_KEY_PASSWORD="KEY_PASSWORD"
```

`local.properties`、keystore 和密码不要提交到 Git。所有公开版本必须使用同一份 keystore，否则 Android 不接受覆盖安装。keystore 至少保留两份离线备份。

## 发布新版本

以发布 `0.1.1` 为例：

0. 先冻结发布候选并检查工作区。发布 APK 必须能对应到唯一提交，不能从混有未跟踪文件和临时改动的目录直接上传：

```powershell
git status --short
git diff --check
git rev-parse HEAD
```

确认需要发布的源码、迁移、资源、测试和文档都已经进入同一个提交，再创建 tag。keystore、`local.properties`、用户备份和真机截图原件不进入提交。

1. 在 `app/build.gradle.kts` 中修改：

```kotlin
versionCode = 2
versionName = "0.1.1"
```

2. 构建 release APK：

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:assembleRelease --no-daemon --console=plain
```

3. 检查 APK 签名与版本：

```powershell
apksigner verify --verbose --print-certs app\build\outputs\apk\release\app-release.apk
aapt dump badging app\build\outputs\apk\release\app-release.apk | Select-String "package:"
Get-FileHash -Algorithm SHA256 app\build\outputs\apk\release\app-release.apk
```

保存签名证书 SHA-256 和 APK SHA-256。后续版本的签名证书指纹必须一致，APK 哈希应写入 Release 说明。

4. 创建 GitHub Release 并上传 APK：

```powershell
gh release create v0.1.1 `
  app\build\outputs\apk\release\app-release.apk `
  --repo kernelx30/Mira `
  --title "Mira 0.1.1" `
  --generate-notes
```

每个正式 Release 只上传一个 `.apk`。应用会读取最新 Release 的 tag，与当前 `versionName` 比较，并下载其中第一个 `.apk`。

5. 分别完成两条真机链路：

- 干净安装：首次启动、模型连接、聊天、语音、记忆、插件和权限入口无崩溃。
- 覆盖升级：从上一公开版本安装新版本，会话、角色、记忆、模型配置和权限状态按设计保留。

最后在旧版本中打开 Mira 的更新检查，确认能够发现、下载并覆盖安装新版本。

## 首次重置版本注意事项

旧测试包使用过 `versionCode = 44`。重置为 `versionCode = 1` 后，Android 会把新包视为降级版本。测试设备需要先卸载旧测试包，再安装新的 `0.1.0`。从首次公开发布开始，`versionCode` 只增不减。

## 同步 Operit 上游

不要直接在有未提交改动的 `main` 上硬合并。先提交 Mira 当前改动，再建立同步分支：

```powershell
git fetch upstream
git switch main
git switch -c sync/operit-YYYYMMDD
git merge upstream/main
```

优先检查这些容易冲突或被上游覆盖的位置：

- `applicationId`、版本号和更新仓库配置
- Mira 名称、图标、默认头像和字符串资源
- Manifest、签名配置和包名相关代码
- Mira 自己新增或修改的功能

解决冲突并验证后，将同步分支合回 `main`：

```powershell
git switch main
git merge --no-ff sync/operit-YYYYMMDD
git push origin main
```

改动较小且目标明确时，可以只挑选上游提交：

```powershell
git fetch upstream
git cherry-pick COMMIT_SHA
```

## 发布前检查表

- `versionCode` 已递增。
- `versionName` 与 Release tag 一致。
- 发布 APK 对应一个已推送且可追溯的干净提交。
- 单测、Release 构建和 `lintVitalRelease` 通过。
- 使用 Mira 固定 release keystore 签名。
- 安装包的 `applicationId` 为 `com.ai.assistance.mira`。
- APK 与签名证书 SHA-256 已记录。
- 更新源仍为 `kernelx30/Mira`。
- Release 不是 Draft，并且包含唯一的正式 APK。
- 已验证从上一公开版本覆盖安装，用户数据仍在。
- keystore 和密码备份完好。
