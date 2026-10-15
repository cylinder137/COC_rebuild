# coc-platform packer

把 Scratch 项目（`.sb` / `.sb2` / `.sb3` / `project.json`）打包成**单文件 HTML 播放器**，
供后端 `/run/{project_id}/` 直出。实现基于 [`@turbowarp/packager`](https://www.npmjs.com/package/@turbowarp/packager) `3.13.0`（**精确锁版本**，该库不保证次版本 API 兼容）。

- 监听：`127.0.0.1:8091`（可用 `COC_PACKER_PORT` 覆盖）
- 启动：`ops\start-packer.cmd`，或脱离会话 `wscript.exe ops\start-packer.vbs`
- 日志：`ops\logs\packer.log`

---

## 接口

### `GET /health`

```json
{"status":"ok","packager":"turbowarp","version":"1.0.0","soft_timeout_ms":3000,"packager_error":null,"port":8091}
```

`packager` 取 `turbowarp`（真实打包可用）或 `fallback`（模块加载失败，只能产出占位页）。
`packager_error` 会带上失败原因。

### `POST /pack`

请求（**后端实际发送的 body，以此为准**）：

```json
{
  "project_id": 2,
  "project_version_id": 3,
  "project_file_id": 5,
  "file_name": "game.sb3",
  "source_path": "E:\\personalProjects\\coc-platform\\.data\\projects\\2\\v1\\source\\game.sb3",
  "output_dir": "E:\\personalProjects\\coc-platform\\.data\\projects\\2\\v1\\build",
  "file_type": "scratch"
}
```

兼容字段：`storage_path`（= `source_path`）、`version_no` + `project_id`（用于推导 `output_dir`，
不传 `output_dir` 时会拼成 `<repo>\.data\projects\{project_id}\v{version_no}\build`）。
`file_name` 也可以从 `source_path` 推导。请求体上限 2 MB。

**产物固定写到 `<output_dir>\index.html`**，并在同目录追加 `pack.log`（后端把该路径记在
`build_task.log_path`）。

响应（HTTP 恒为 200，除非 JSON 非法 400 / 体积超限 413）：

```json
{
  "ok": true,
  "fallback": false,
  "output_path": "E:\\...\\v1\\build\\index.html",
  "build_path":  "E:\\...\\v1\\build\\index.html",
  "packager": "turbowarp",
  "ms": 34,
  "size": 1827993,
  "engine_ms": 32
}
```

- `ok:true` 或 `status:"ok"` → 后端把 `build_task.status` 置 2（成功）、
  `project_file.build_status=2`、`build_path=<产物>`。
- 后端**只等 4 秒**（`coc.packer.timeout-ms=4000`），超时/失败会降级成
  `build_task.status=0`（待处理），可走 `POST /projects/{id}/build/retry`。

---

## 为什么能稳在 4 秒内

`@turbowarp/packager` 的 Node API 是纯内存操作，本项目 demo 实测：

| 阶段 | 耗时 |
| --- | --- |
| `loadProject`（解析 sb3） | 10–33 ms |
| `package()`（生成单文件 HTML） | 11–22 ms |
| **合计** | **25–55 ms**（产物约 1.83 MB） |

所以采用**同步打包**，但仍加了两层保险：

1. **软超时** `COC_PACKER_SOFT_TIMEOUT_MS`（默认 **3000 ms**）：超过就放弃本次，
   写占位页并立刻返回，绝不拖过后端的 4 秒。
2. **降级 + 后台重试**：一旦失败/超时，立刻写占位播放器 → 返回 `{ok:true, fallback:true, reason}`，
   同时在**后台**再跑一次不限时的真实打包，成功就覆盖 `index.html`。

→ 最坏情况是「能用，但先是占位页，随后被真产物替换」，后端**永远不会因打包卡住**。

## 降级行为一览

| 情况 | 返回 | 产物 |
| --- | --- | --- |
| 正常 sb/sb2/sb3 | `ok:true, fallback:false` | 真实 Turbowarp 播放器 |
| 软超时 / 解析失败 / 文件损坏 | `ok:true, fallback:true, reason` | 占位播放器（+ 后台重试覆盖） |
| `@turbowarp/packager` 装不上 | `ok:true, fallback:true` | 占位播放器 |
| `source_path` 不存在 | `ok:false, fallback:true, reason` | 占位播放器 |
| 非 Scratch 文件（.html / .mcstructure） | `ok:false, fallback:false, reason:"not a scratch file"` | 不写 |
| 请求体非法 JSON | HTTP 400 | 不写 |

> 后端只在 `project_type=scratch` 时才触发打包，所以「非 Scratch → ok:false」不会影响
> html / structure 项目的正常流程。

## 命令行

```cmd
cd packer
npm start                                        :: 起服务
node pack-cli.js <input.sb3> [output.html]       :: 单独打包一个文件，打印耗时
node pack-cli.js ..\.data\projects\203\v1\source\project.sb3
```

## 环境变量

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `COC_PACKER_PORT` | `8091` | 监听端口 |
| `COC_PACKER_HOST` | `127.0.0.1` | 监听地址 |
| `COC_PACKER_SOFT_TIMEOUT_MS` | `3000` | 同步打包软超时（务必 < 后端 4000） |

## 依赖与体积

- `package.json` 里 `@turbowarp/packager` 写的是**精确版本** `3.13.0`（该库不承诺 API 稳定）。
- 该库有「大资源按需下载 + 本地缓存」机制，缓存目录
  `node_modules\@turbowarp\packager\.packager-cache`（本次打包 sb3 未触发下载，纯离线可用）。
- 生成的单文件 HTML 约 **1.83 MB**（内联 VM 运行时），属正常。
