---
fork: https://github.com/kernelx30/Mira
status: implemented
---

# Mira 悬浮窗自动朗读

## 原因

主聊天和悬浮窗分别使用 `MAIN` 与 `FLOATING` 两套 `ChatServiceCore`。主聊天由 `ChatViewModel` 将 `speakMessageHandler` 绑定到真实 TTS，悬浮窗实例保留的是日志占位处理器，导致自动朗读流程命中后只记录日志、不播放声音。

## 修改

- 新增 `FloatingAutoReadController`，复用当前 TTS 服务、清理规则、语速、音调和情感语音规划
- 将 `FLOATING` 核心的朗读处理器绑定到真实播放链
- 普通悬浮窗、悬浮球和结果页遵循现有自动朗读开关
- 全屏语音与 OCR 全屏保留自身的流式 TTS，核心朗读在这些模式下停用，防止重复播放
- 进入全屏语音或 OCR 前主动停止普通悬浮窗朗读，避免两条 TTS 链争用同一语音服务
- 中断时作废整条分段播放队列，防止旧分段在停止后重新开始播放
- 悬浮窗关闭时解除核心处理器并停止当前播放，避免持有 Service 和残留音频

## 验收

- 开启自动朗读后，从普通悬浮窗发送消息，AI 回复会调用当前配置的 TTS
- 关闭自动朗读后，普通悬浮窗只显示文本
- 全屏语音回复保持单路播放
- 切换到全屏语音时，普通悬浮窗尚未结束的朗读停止

[DONE]
