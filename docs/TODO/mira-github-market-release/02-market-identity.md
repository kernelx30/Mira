# 02 市场身份隔离

- 禁止向非 GitHub 域名传递 GitHub Token
- 移除旧动态市场 Session 缓存
- 注销与换号后市场请求实时读取当前 GitHub Token
- GitHub 仓库发布链保持可用
- 明确评论、点赞和统计的 Mira 服务边界

## 状态

[DONE] 新条目写入走 GitHub Issue / Comment / Reaction；旧 Operit 条目只读展示，不接收 Mira 账号写入。
