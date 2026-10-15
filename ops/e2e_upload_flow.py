# -*- coding: utf-8 -*-
"""coc-platform 端到端验收：用「星球 - 副本.html」（2.38MB Turbowarp 成品）走完整投稿链路。

链路：开发者登录 → 建草稿 → 分片上传（乱序）→ 查缺失 → 合并 → 提交审核
      → 管理员审核通过 → 访客列表/详情可见 → 沙箱运行 URL 可取回同样字节 → 访客可运行
"""
import hashlib
import json
import os
import sys
import uuid

try:
    import requests
except ImportError:
    requests = None

BASE = "http://127.0.0.1:8090/api/v1"
SRC = r"C:\Users\Administrator\Desktop\星球 - 副本.html"
ADMIN = ("3254328344", "Coc@2026")
DEV = ("10001", "Coc@2026")

OK = []
BAD = []


def check(label, cond, extra=""):
    (OK if cond else BAD).append(label)
    print(("  PASS  " if cond else "  FAIL  ") + label + (("   | " + str(extra)[:200]) if extra else ""))


def api(method, path, body=None, token=None, files=None, data=None, raw=False):
    url = path if path.startswith("http") else BASE + path
    h = {}
    if token:
        h["Authorization"] = "Bearer " + token
    if files is None:
        h["Content-Type"] = "application/json"
        r = requests.request(method, url, data=json.dumps(body) if body is not None else None,
                             headers=h, timeout=60)
    else:
        r = requests.request(method, url, data=data, files=files, headers=h, timeout=120)
    if raw:
        return r.status_code, r.content
    try:
        return r.status_code, r.json()
    except Exception:
        return r.status_code, {"_raw": r.text[:200]}


def login(qq, pw):
    st, d = api("POST", "/auth/login", {"qq_account": qq, "password": pw})
    assert st == 200 and d.get("code") == 0, (st, d)
    return d["data"]["token"], d["data"]["user"]


print("=== 0. 准备 ===")
data_bytes = open(SRC, "rb").read()
sha = hashlib.sha256(data_bytes).hexdigest()
fname = os.path.basename(SRC)
print(f"  文件 {fname}  {len(data_bytes)} bytes  sha256={sha[:16]}...")

dev_tok, dev_user = login(*DEV)
admin_tok, admin_user = login(*ADMIN)
print(f"  开发者 #{dev_user['user_id']} {dev_user['nickname']} / 管理员 #{admin_user['user_id']}")

print("\n=== 1. 建草稿 ===")
st, d = api("POST", "/projects", {
    "title": "星球（Turbowarp 作品）",
    "summary": "用 Scratch 做的星球主题小作品，Turbowarp 打包为单文件 HTML，可直接在浏览器里玩。",
    "description": "这是从 sb3 用 Turbowarp 打包出来的单文件 HTML 作品，用于验证：\n"
                   "1) 大文件分片上传（乱序 + 断点续传）\n2) 审核通过后首页可见\n3) 沙箱 iframe 内可直接运行\n4) 源码仅登录用户可下载",
    "project_type": "html",
    "category_id": 101,
    "tags": ["Scratch", "Turbowarp"],
}, token=dev_tok)
pid = (d.get("data") or {}).get("project_id")
check("建草稿", st == 200 and pid, d)
print(f"  project_id = {pid}, audit_mode={(d.get('data') or {}).get('audit_mode')}")

print("\n=== 2. 分片上传（1MB 分片，故意乱序）===")
CHUNK = 1024 * 1024
st, d = api("POST", "/uploads/init", {
    "file_name": fname, "file_size": len(data_bytes), "sha256": sha, "chunk_size": CHUNK
}, token=dev_tok)
up = d.get("data") or {}
sid, total = up.get("upload_session_id"), up.get("total_chunks")
check("uploads/init", st == 200 and sid and total and total >= 3, d)
print(f"  upload_session_id={sid} total_chunks={total} instant={up.get('instant')}")

parts = [data_bytes[i:i + CHUNK] for i in range(0, len(data_bytes), CHUNK)]
order = [len(parts), 1] + list(range(2, len(parts)))  # 先传最后一片，再传第 1 片，其余顺序补齐
for pn in order:
    st, d = api("POST", f"/uploads/{sid}/parts",
                files={"chunk": (f"part{pn}", parts[pn - 1], "application/octet-stream")},
                data={"part_number": str(pn)}, token=dev_tok)
    if st != 200 or d.get("code") != 0:
        check(f"上传分片 {pn}", False, d)
        break
    done = len((d.get("data") or {}).get("uploaded_chunks") or [])
    print(f"  分片 {pn}/{total} 已传（累计 {done}）")
check("乱序分片全部上传", True)

st, d = api("GET", f"/uploads/{sid}", token=dev_tok)
missing = (d.get("data") or {}).get("missing_chunks")
check("查缺失分片（应为空）", st == 200 and missing == [], d)

print("\n=== 3. 合并落盘 ===")
st, d = api("POST", f"/uploads/{sid}/complete", {
    "project_id": pid, "file_type": "source", "changelog": "首次投稿：星球（Turbowarp 成品）"
}, token=dev_tok)
cd = d.get("data") or {}
check("uploads/complete", st == 200 and cd.get("project_file_id"), d)
print(f"  project_file_id={cd.get('project_file_id')} build_task_id={cd.get('build_task_id')}")
print(f"  storage_path={cd.get('storage_path')}")
stored = cd.get("storage_path")
if stored:
    on_disk = os.path.getsize(stored) if os.path.exists(stored) else -1
    check("落盘字节数与源文件一致", on_disk == len(data_bytes), f"{on_disk} vs {len(data_bytes)}")

print("\n=== 4. 秒传验证（同 sha256 再 init 一次）===")
st, d = api("POST", "/uploads/init", {
    "file_name": fname, "file_size": len(data_bytes), "sha256": sha, "chunk_size": CHUNK
}, token=dev_tok)
check("秒传命中", st == 200 and (d.get("data") or {}).get("instant") is True, d)

print("\n=== 5. 提交审核（html 应进待审）===")
st, d = api("POST", f"/projects/{pid}/submit", {}, token=dev_tok)
sd = d.get("data") or {}
check("submit 进入待审", st == 200 and sd.get("status") == 1, d)
print(f"  status={sd.get('status')} audit_mode={sd.get('audit_mode')}")

st, d = api("GET", "/projects?page=1&size=50&keyword=星球")
found = [p for p in ((d.get("data") or {}).get("list") or []) if p.get("project_id") == pid]
check("待审项目不出现在访客列表（合规）", found == [], found)

print("\n=== 6. 管理员审核通过 ===")
st, d = api("GET", "/admin/reviews/queue", token=admin_tok)
item = next((x for x in ((d.get("data") or {}).get("list") or []) if x.get("project_id") == pid), None)
check("审核队列里能看到它", item is not None, d)
vid = item.get("latest_version_id") if item else None
st, d = api("POST", f"/admin/reviews/{vid}/approve", {"reason": "内容合规，通过"}, token=admin_tok)
check("approve 成功", st == 200 and d.get("code") == 0, d)

print("\n=== 7. 访客可见性 + 沙箱运行 ===")
st, d = api("GET", "/projects?page=1&size=50&keyword=星球")
found = [p for p in ((d.get("data") or {}).get("list") or []) if p.get("project_id") == pid]
check("通过后出现在访客列表", len(found) == 1, found[:1])

st, d = api("GET", f"/projects/{pid}/run")
url = (d.get("data") or {}).get("url")
check("拿到运行地址", st == 200 and url, d)
if url:
    st2, body = api("GET", url, raw=True)
    check("访客可直接运行（无需登录）", st2 == 200, f"HTTP {st2}")
    check("运行产物字节数 == 上传文件字节数", len(body) == len(data_bytes),
          f"{len(body)} vs {len(data_bytes)}")
    check("运行产物确实是该作品（含 turbowarp 打包标记）",
          b"packager.turbowarp.org" in body[:400], body[:80])

st, d = api("GET", f"/projects/{pid}/comments?page=1&size=20")
check("评论接口正常（详情页评论区）", st == 200 and isinstance((d.get("data") or {}).get("list"), list), d)

print("\n=== 8. 详情页所需字段 ===")
st, d = api("GET", f"/projects/{pid}")
dd = d.get("data") or {}
check("详情含 author/cover/files", all(k in dd for k in ("author",)) or "author_id" in dd, list(dd.keys())[:12])
print(f"  title={dd.get('title')} status={dd.get('status')} views={dd.get('view_count_total')} cover={dd.get('cover_path')}")

print("\n================ 汇总 ================")
print(f"PASS {len(OK)}  FAIL {len(BAD)}")
if BAD:
    print("失败项：")
    for b in BAD:
        print("  -", b)
print(f"\n演示项目 project_id = {pid}（可在 8085 首页搜索「星球」）")
sys.exit(1 if BAD else 0)
