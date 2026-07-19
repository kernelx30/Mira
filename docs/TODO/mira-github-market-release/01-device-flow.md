# 01 GitHub Device Flow

- 请求 device code 并显示 verification URI 与 user code
- 按服务端 interval 轮询 access token
- 处理 pending、slow down、拒绝、过期、取消和超时
- 使用临时 Token 验证 `/user`，成功后再保存登录态
- 移除 authorization-code、Client Secret 和旧回调依赖

## 状态

[DONE] GitHub 登录已切到 Device Flow，旧回调 Activity 和 WebView 登录界面已清理，旧登录态版本已提升。
