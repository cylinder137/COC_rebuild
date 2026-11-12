# 造物集 App 端前端（副本）

## 这是什么

`frontend-app` 是 `E:\Project Files\COC_rebuild\frontend` 在 **2026-10-19 22:36** 的完整快照副本，
用于将来 App 端与网页端分化开发。

## 当前状态（2026-10-19）

- **只是一个副本**：尚未对 App 端做任何功能变更，也**不对 App 端换源**
- 不含 `node_modules` / `dist` / `dist-user` / `.git`（省 146 MB，需要时自己 `npm i`）
- 与官网主前端（`frontend`）此刻内容一致，之后两边各自演进

## 现状说明：当前 APK 用的是哪份代码

**已发布的 v1.0 APK（`uk.collectionofcreations.coc`）内嵌的是 `frontend` 当时的 `dist`，不是本副本。**

也就是说目前 App 端和网页端跑的是同一套代码，只是壳不同。这次的分化是**为将来预留**，
不是已经分家。真要分家时：

1. 在 `frontend-app` 里改代码（例如去掉下载入口、调整移动端布局、换 API 源）
2. 构建：`cd E:\Project Files\COC_rebuild\frontend-app && npm install && npm run build`
3. 打包前把 `build-coc-apk.ps1` 顶部的 `$Frontend` 指向 `frontend-app`，或手工把 `dist` 拷到
   `E:\Server Data\apk-build\coc-app\www` 后 `npx cap sync android`

## 与主前端的关键差异点（分家时会碰到）

| 点 | 网页端 `frontend` | App 端 `frontend-app`（将来） |
| --- | --- | --- |
| 顶栏「下载 App」 | 有（`/downloads/coc-app-1.0.apk`） | 应去掉 |
| 数据源 | `VITE_API_BASE` 指向公网域名 | 可考虑直连或独立域名 |
| 主题/布局 | 桌面优先 | 移动优先 |
| 下载/外部跳转 | 浏览器打开 | 需处理 `window.open` 在 WebView 的行为 |

## 注意

- 两个目录**共享同一 git 仓库**（`COC_rebuild`），`frontend-app` 是新目录，需要时自行 `git add`
- `.env.user.dev-bak` 之类的历史文件一并复制过来了，注意别误用
- 副本里 `README-dev.md` 是主前端的开发说明，仍然适用
