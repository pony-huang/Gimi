# 非废弃 REST 接口清单

来源与边界见 [优先级说明](api-priorities.md)。此清单逐项覆盖核对提交的两个 OpenAPI 文件；不含 envd RPC，后者已单列在优先级说明中。

共 74 个非废弃 REST 操作，其中首版实现 7 个；排除 7 个废弃操作。平台管理类只保留盘点，不进入通用 SDK 实现计划。

## `openapi.yml`（67 项）

| 方法 | 路径 | 功能 | 优先级 | 首版 |
| --- | --- | --- | --- | --- |
| GET | `/health` | Health check | P3 | 未实现 |
| GET | `/teams` | List teams | P3 | 未实现 |
| GET | `/teams/{teamID}/metrics` | Team metrics | P1 | 未实现 |
| GET | `/teams/{teamID}/metrics/max` | Maximum team metrics | P1 | 未实现 |
| POST | `/v2/sandboxes` | Create sandbox (v2) | P0 | 已实现 |
| GET | `/v2/sandboxes` | List sandboxes (v2) | P0 | 已实现 |
| GET | `/sandboxes/metrics` | List sandbox metrics | P1 | 未实现 |
| GET | `/v2/sandboxes/{sandboxID}/logs` | Sandbox logs (v2) | P1 | 未实现 |
| GET | `/sandboxes/{sandboxID}` | Sandbox | P0 | 已实现 |
| DELETE | `/sandboxes/{sandboxID}` | Kill sandbox | P0 | 已实现 |
| GET | `/sandboxes/{sandboxID}/metrics` | Sandbox metrics | P1 | 未实现 |
| POST | `/sandboxes/{sandboxID}/pause` | Pause sandbox | P1 | 已实现 |
| POST | `/sandboxes/{sandboxID}/fork` | Fork sandbox | P2 | 未实现 |
| POST | `/v2/sandboxes/{sandboxID}/connect` | Connect sandbox (v2) | P0 | 已实现 |
| POST | `/sandboxes/{sandboxID}/timeout` | Set sandbox timeout | P0 | 已实现 |
| PUT | `/sandboxes/{sandboxID}/network` | Update sandbox network | P1 | 未实现 |
| POST | `/sandboxes/{sandboxID}/refreshes` | Refresh sandbox | P1 | 未实现 |
| POST | `/sandboxes/{sandboxID}/snapshots` | Create snapshot | P2 | 未实现 |
| GET | `/snapshots` | List snapshots | P2 | 未实现 |
| POST | `/v3/templates` | Create template (v3) | P2 | 未实现 |
| GET | `/v2/templates` | List templates (v2) | P1 | 已实现分页查询，返回 ID、names 和构建状态 |
| GET | `/templates/{templateID}/files/{hash}` | Template build file upload URL | P2 | 未实现 |
| GET | `/templates/{templateID}` | List template builds | P2 | 未实现 |
| DELETE | `/templates/{templateID}` | Delete template | P2 | 未实现 |
| POST | `/v2/templates/{templateID}/builds/{buildID}` | Start template build (v2) | P2 | 未实现 |
| PATCH | `/v2/templates/{templateID}` | Update template (v2) | P2 | 未实现 |
| GET | `/templates/{templateID}/builds/{buildID}/status` | Template build status | P2 | 未实现 |
| GET | `/templates/{templateID}/builds/{buildID}/logs` | Template build logs | P2 | 未实现 |
| POST | `/templates/tags` | Assign template tags | P1 | 未实现 |
| DELETE | `/templates/tags` | Delete template tags | P1 | 未实现 |
| GET | `/templates/{templateID}/tags` | List template tags | P1 | 未实现 |
| GET | `/templates/aliases/{alias}` | Check template alias | P1 | 未实现 |
| GET | `/nodes` | List nodes | 平台管理 | 未实现 |
| GET | `/nodes/{nodeID}` | Node info | 平台管理 | 未实现 |
| POST | `/nodes/{nodeID}` | Change node status | 平台管理 | 未实现 |
| POST | `/admin/teams/{teamID}/sandboxes/kill` | Kill all sandboxes for a team | 平台管理 | 未实现 |
| GET | `/admin/sandboxes/running-counts` | Count running sandboxes by team | 平台管理 | 未实现 |
| POST | `/admin/teams/{teamID}/builds/cancel` | Cancel all builds for a team | 平台管理 | 未实现 |
| POST | `/admin/teams/{teamID}/api-keys` | Create team API key as admin | 平台管理 | 未实现 |
| DELETE | `/admin/teams/{teamID}/api-keys/{apiKeyID}` | Delete team API key as admin | 平台管理 | 未实现 |
| GET | `/api-keys` | List team API keys | P3 | 未实现 |
| POST | `/api-keys` | Create team API key | P3 | 未实现 |
| PATCH | `/api-keys/{apiKeyID}` | Update team API key | P3 | 未实现 |
| DELETE | `/api-keys/{apiKeyID}` | Delete team API key | P3 | 未实现 |
| GET | `/volumes` | List team volumes | P2 | 未实现 |
| POST | `/volumes` | Create team volume | P2 | 未实现 |
| GET | `/volumes/{volumeID}` | Team volume | P2 | 未实现 |
| DELETE | `/volumes/{volumeID}` | Delete team volume | P2 | 未实现 |
| GET | `/secrets` | List project secrets | P2 | 未实现 |
| POST | `/secrets` | Create a secret | P2 | 未实现 |
| GET | `/secrets/{secretID}` | Get a secret | P2 | 未实现 |
| POST | `/secrets/{secretID}` | Update a secret | P2 | 未实现 |
| DELETE | `/secrets/{secretID}` | Delete a secret | P2 | 未实现 |
| GET | `/clusters/{clusterID}/rigs` | List rigs of a cluster | 平台管理 | 未实现 |
| PUT | `/clusters/{clusterID}/rigs/{rigID}/capacity` | Set the capacity of a rig | 平台管理 | 未实现 |
| DELETE | `/clusters/{clusterID}/rigs/instances/{instanceID}` | Terminate an instance of a rig | 平台管理 | 未实现 |
| GET | `/clusters/{clusterID}/rigs/{rigID}/instances` | List the instances attached to a rig | 平台管理 | 未实现 |
| GET | `/clusters/{clusterID}/rigs/{rigID}/errors` | List recent scaling errors of a rig | 平台管理 | 未实现 |
| GET | `/events/sandboxes/{sandboxID}` | 查询单沙箱事件 | P3 | 未实现 |
| GET | `/events/sandboxes` | 查询沙箱事件 | P3 | 未实现 |
| POST | `/events/webhooks` | 创建 Webhook | P3 | 未实现 |
| GET | `/events/webhooks` | 列举 Webhook | P3 | 未实现 |
| GET | `/events/webhooks/{webhookID}` | 查询 Webhook | P3 | 未实现 |
| PATCH | `/events/webhooks/{webhookID}` | 更新 Webhook | P3 | 未实现 |
| DELETE | `/events/webhooks/{webhookID}` | 删除 Webhook | P3 | 未实现 |
| GET | `/events/webhooks/{webhookID}/deliveries` | 查询投递记录 | P3 | 未实现 |
| GET | `/events/webhooks/{webhookID}/stats` | 查询投递统计 | P3 | 未实现 |

## `openapi-volumecontent.yml`（7 项）

| 方法 | 路径 | 功能 | 优先级 | 首版 |
| --- | --- | --- | --- | --- |
| GET | `/volumecontent/{volumeID}/path` | Get path information | P2 | 未实现 |
| PATCH | `/volumecontent/{volumeID}/path` | Update path metadata | P2 | 未实现 |
| DELETE | `/volumecontent/{volumeID}/path` | Delete a path | P2 | 未实现 |
| GET | `/volumecontent/{volumeID}/dir` | List directory contents | P2 | 未实现 |
| POST | `/volumecontent/{volumeID}/dir` | Create a directory | P2 | 未实现 |
| GET | `/volumecontent/{volumeID}/file` | Download file | P2 | 未实现 |
| PUT | `/volumecontent/{volumeID}/file` | Upload file | P2 | 未实现 |
