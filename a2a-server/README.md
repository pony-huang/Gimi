# a2a-server — gimi A2A 客户端联调用示例服务

基于官方 `a2a-sdk`（1.x，protobuf 类型层）的 Sample Agent，供 gimi 的 A2A
客户端（adk-kotlin + a2a-java，只消费 **JSON-RPC** over HTTP）做端到端联调。

由 `temp/a2a-server` 迁移而来（uv 项目）；`temp/` 下的旧副本仅为兼容当时
正在运行的服务实例。

## 运行

```bash
uv sync
uv run server.py --advertise-host 10.1.2.197 --port 41241
```

- `--advertise-host`：写入 Agent Card 的对外地址。**本机跑 Clash TUN 时自动探测
  会拿到 198.18.x.x（虚拟网卡），必须显式指定真实局域网 IP**（以 `ipconfig`
  实时值为准）；Tailscale 通道可用对应 100.x 地址。
- `--enable-grpc`：追加 gRPC 1.0 传输（依赖改为 `a2a-sdk[http-server,grpc]`）。
- `--enable-v0-3-compat`：卡片追加 0.3 兼容 interface + 0.3 路由（gimi 客户端用不到）。

## 测试

行为契约测试（进程内 ASGI，无需起服务、无真实网络）：

```bash
uv run pytest
```

覆盖：1.0 卡片 interface、非流式 SendMessage 往返、流式 SendStreamingMessage
的 SSE 生命周期（submitted→working→artifact(lastChunk)→completed）、
`A2A-Version` 头缺失被拒、0.3 蛇形方法名不被识别。

## 手机端接入（gimi）

1. 手机与 PC 同一 Wi-Fi（或手机登入同一 Tailscale）。
2. gimi → 设置 → 远程 Agent → 添加：
   - 名称：任意（如 `sample_agent`，同时是委派目标名）
   - Agent 地址：`http://10.1.2.197:41241`
   - 保存 → 编辑器自动做连接测试（拉取 `/.well-known/agent-card.json`）。
3. 会话里说 "hi" 之类触发委派，回复为 `Hello World! ...`。

### Windows 防火墙

首次监听若弹窗请放行 Python；或手动放行端口：

```powershell
netsh advfirewall firewall add rule name="A2A demo 41241" dir=in action=allow protocol=TCP localport=41241
```

## 1.x 线协议要点（手测 cheat sheet）

JSON-RPC 方法名为 **PascalCase**（`SendMessage` / `SendStreamingMessage` /
`GetTask`…），与 0.3 的 `message/send` 不同，且请求必须带 `A2A-Version: 1.0` 头：

```bash
curl -s --noproxy "*" -X POST http://127.0.0.1:41241/a2a/jsonrpc \
  -H "Content-Type: application/json" -H "A2A-Version: 1.0" \
  -d '{"jsonrpc":"2.0","id":1,"method":"SendMessage","params":{"message":{"role":"ROLE_USER","parts":[{"text":"hi"}],"messageId":"m1"}}}'
```

注意本机代理：curl 测本机端口加 `--noproxy "*"`，避免请求被 Clash 代理劫持。
