---
fork: https://github.com/kernelx30/Mira
status: done
---

# Mira GitHub 与市场发布链迁移

## 现状

Release 构建不嵌入 GitHub Client Secret。旧授权码登录、旧回调和旧市场 Session 链路已经迁移为 GitHub Device Flow 与 GitHub 原生市场身份。

## 目标

- GitHub 登录使用 Device Flow，只公开 OAuth App Client ID
- 临时 Token 验证用户成功后再持久化
- GitHub Token 只发送给 GitHub 官方 API
- 市场身份与当前 Token 指纹绑定，注销和换号立即失效
- 插件发布继续直连 GitHub，Operit 插件格式保持兼容
- README、用户教程和插件文档完整说明 Mira 新增能力与隐私边界

## 结果

- GitHub 登录使用 Device Flow，公开 APK 只携带公开 Client ID。
- Token 先通过 GitHub `/user` 验证，再保存到本地登录状态。
- 新市场条目、评论、点赞和管理走 GitHub Issue / Comment / Reaction。
- 旧 Operit 市场条目作为只读兼容源展示，不接收 Mira GitHub Token。
- README、用户教程、插件开发文档、隐私说明和发布说明已补齐 Mira 新增特性与数据边界。

## 作用域

- GitHub OAuth API、协调器、登录界面和旧回调清理
- 市场身份、评论、点赞、统计和管理调用
- OAuth 与市场身份单元测试
- README、用户教程、插件开发文档及发布说明
