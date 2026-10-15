import json, urllib.request, urllib.error, sys

BASE = "http://127.0.0.1:8090/api/v1"

def call(method, path, body=None, token=None, raw=False):
    url = path if path.startswith("http") else BASE + path
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            b = r.read()
            if raw:
                return r.status, b
            try:
                return r.status, json.loads(b.decode("utf-8", "replace"))
            except Exception:
                return r.status, b[:200]
    except urllib.error.HTTPError as e:
        b = e.read()
        try:
            return e.code, json.loads(b.decode("utf-8", "replace"))
        except Exception:
            return e.code, b[:200]
    except Exception as e:
        return -1, str(e)

def show(label, st, data, limit=220):
    s = json.dumps(data, ensure_ascii=False) if not isinstance(data, (str, bytes)) else str(data)
    print(f"[{st}] {label}: {s[:limit]}")

print("=== 1. health / 端口 ===")
show("8090 health", *call("GET", "/system/health"))
show("8091 packer health", *call("GET", "http://127.0.0.1:8091/health"))

print("\n=== 2. 首页前端 ===")
for p in (8085, 8086):
    st, b = call("GET", f"http://127.0.0.1:{p}/", raw=True)
    print(f"[{st}] :{p}/ -> {'<div id=\"app\">' in b.decode('utf-8','replace')}")

print("\n=== 3. 访客读（零写库）===")
st, d = call("GET", "/projects?page=1&size=20")
items = (d.get("data") or {}).get("list") if isinstance(d, dict) else None
print(f"[{st}] 项目列表 total={(d.get('data') or {}).get('total') if isinstance(d,dict) else '?'}")
ids = []
if items:
    for it in items[:8]:
        print("   -", it.get("project_id"), it.get("title"), it.get("project_type"), "status", it.get("status"), "views", it.get("view_count_total"))
        ids.append((it.get("project_id"), it.get("project_type")))
st, d = call("GET", "/categories"); show("分类", st, d, 160)

print("\n=== 4. 沙箱运行（访客可跑）===")
for pid, _t in ids[:3]:
    st, d = call("GET", f"/projects/{pid}/run")
    url = (d.get("data") or {}).get("url") if isinstance(d, dict) else None
    print(f"   run({pid}) -> [{st}] url={url}")
    if url:
        st2, b = call("GET", url, raw=True)
        body = b.decode("utf-8", "replace") if isinstance(b, bytes) else str(b)
        print(f"      GET {url} -> [{st2}] {len(body) if isinstance(body,str) else 0} bytes, html={'<html' in body.lower() or '<!doctype' in body.lower()}")

print("\n=== 5. 详情 + 统计折线 ===")
show("详情 201", *call("GET", "/projects/201"), 300)
show("stats range=7", *call("GET", "/projects/201/stats?range=7"), 300)
show("stats range=2", *call("GET", "/projects/201/stats?range=2"), 200)
show("评论 201", *call("GET", "/projects/201/comments?page=1&size=20"), 260)

print("\n=== 6. 访客写 → 必须被拒 ===")
for m, p, b in [("POST", "/projects/201/like", None), ("POST", "/comments", {"project_id": 201, "content": "访客不该成功"}), ("GET", "/files/999/content", None)]:
    show(f"访客 {m} {p}", *call(m, p, b))

print("\n=== 7. 登录 + 鉴权链路 ===")
st, d = call("POST", "/auth/login", {"qq_account": "3254328344", "password": "Coc@2026"})
print(f"[{st}] 管理员登录")
tok = (d.get("data") or {}).get("token") if isinstance(d, dict) else None
print("   token:", (tok[:24] + "...") if tok else None, "| user:", (d.get("data") or {}).get("user") if isinstance(d, dict) else None)
if tok:
    show("auth/me", *call("GET", "/auth/me", token=tok), 200)
    show("admin stats", *call("GET", "/admin/stats/platform", token=tok), 300)
    show("admin 审核队列", *call("GET", "/admin/reviews/queue", token=tok), 260)
    show("admin 存储路径 201", *call("GET", "/admin/projects/201/storage", token=tok), 300)
st, d = call("POST", "/auth/login", {"qq_account": "10001", "password": "Coc@2026"})
dev = (d.get("data") or {}).get("token") if isinstance(d, dict) else None
print(f"[{st}] 开发者登录 token={'有' if dev else '无'}")
if dev:
    show("我的作品", *call("GET", "/users/me/projects", token=dev), 220)
    st2, real = call("GET", "/projects/201", token=dev)
    pf = ((real.get("data") or {}).get("files") or [{}]) if isinstance(real, dict) else [{}]
    fid = pf[0].get("project_file_id") if pf else None
    print("   源码文件 id:", fid)
    if fid:
        st3, b = call("GET", f"/files/{fid}/content", token=dev, raw=True)
        print(f"   [登录] 下载源码 -> [{st3}] {len(b) if isinstance(b,bytes) else 0} bytes")
        show("   [访客] 同一文件", *call("GET", f"/files/{fid}/content"))

print("\n=== 8. 封面静态资源 ===")
st, b = call("GET", "http://127.0.0.1:8090/covers/201.png", raw=True)
print(f"[{st}] :8090/covers/201.png -> {b[:80] if isinstance(b,bytes) else b}")
print("\nSMOKE DONE")
