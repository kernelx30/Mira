---
fork: https://github.com/kernelx30/Mira
feature: Mira conversational settings
status: active
---

# Mira 对话设置

## 现状

模型、语音、包与 MCP 已有部分工具，但普通聊天没有统一、稳定的设置目录。界面设置分散在多个 DataStore 和管理器，直接暴露存储键会绕开校验与副作用处理。

## 目标

- 使用一个 `manage_mira_settings` 工具完成搜索、读取和修改
- 设置页面与聊天工具调用相同的数据源
- 先覆盖高频、可逆且业务含义明确的 Mira 设置
- 密钥、清空、重置、导入覆盖和系统权限保持专用流程
- 修改后返回旧值、新值和是否立即生效

## 作用域

- `core/settings/`：设置注册表、值类型、查询和修改结果
- `core/tools/`：工具执行、注册和模型提示
- `data/preferences/`：复用已有公开读写 API
- 单元测试和用户教程

## 非目标

- 不替换现有设置页面
- 不暴露内部 DataStore 键
- 不通过通用工具读取完整 API Key
- 不把 Android 系统权限伪装成应用内布尔设置

