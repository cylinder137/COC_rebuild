# -*- coding: utf-8 -*-
"""① 清理 E2E 产生的测试项目 ② 复验秒传（修复后：不得删除被引用项目的源文件）"""
import hashlib, json, os
import requests

BASE = "http://127.0.0.1:8090/api/v1"
SRC = r"C:\Users\Administrator\Desktop\星球 - 副本.html"
data = open(SRC, "rb").read()
sha = hashlib.sha256(data).hexdigest()
fname = os.path.basename(SRC)


def api(m, p, body=None, token=None, files=None, data_=None):
    h = {"Authorization": "Bearer " + token} if token else {}
    if files is None:
        h["Content-Type"] = "application/json"
        r = requests.request(m, BASE + p, data=json.dumps(body) if body is not None else None, headers=h, timeout=60)
    else:
        r = requests.request(m, BASE + p, data=data_, files=files, headers=h, timeout=120)
    try:
        return r.status_code, r.json()
    except Exception:
        return r.status_code, {"_raw": r.text[:300]}


def login(qq, pw):
    st, d = api("POST", "/auth/login", {"qq_account": qq, "password": pw})
    return d["data"]["token"]


admin = login("3254328344", "Coc@2026")
dev = login("10001", "Coc@2026")

print("=== 1. 清理 E2E 测试项目 ===")
st, d = api("GET", "/admin/projects?page=1&size=100&include_deleted=0", token=admin)
rows = (d.get("data") or {}).get("list") or []
KEEP = {201, 202, 203, 204, 205, 206, 208}
for r in rows:
    pid = r.get("project_id")
    title = r.get("title") or ""
    if pid in KEEP:
        continue
    st2, d2 = api("DELETE", f"/admin/projects/{pid}", token=admin)
    print(f"  删除 project {pid} {title[:24]!r} -> {st2} {d2.get('code')}")

print("\n=== 2. 复验秒传（引用项目 208 的文件）===")
st, d = api("POST", "/projects", {
    "title": "星球（秒传复验）", "summary": "验证秒传不再破坏被引用项目",
    "description": "秒传复验", "project_type": "html", "category_id": 101,
}, token=dev)
pid = d["data"]["project_id"]
print(f"  新草稿 project_id={pid}")

st, d = api("POST", "/uploads/init", {"file_name": fname, "file_size": len(data), "sha256": sha}, token=dev)
instant = (d.get("data") or {}).get("instant")
sid = (d.get("data") or {}).get("upload_session_id")
print(f"  init -> instant={instant} upload_session_id={sid}")

st, d = api("POST", f"/uploads/{sid}/complete", {"project_id": pid, "file_type": "source", "changelog": "秒传复验"}, token=dev)
print(f"  complete -> [{st}] {json.dumps(d, ensure_ascii=False)[:220]}")
path_new = (d.get("data") or {}).get("storage_path")

ok_instant = st == 200 and d.get("code") == 0
p208 = r"E:\personalProjects\coc-platform\.data\projects\208\v1\source\星球 - 副本.html"
ok_208 = os.path.exists(p208) and os.path.getsize(p208) == len(data)
ok_new = bool(path_new) and os.path.exists(path_new) and os.path.getsize(path_new) == len(data)
print(f"  秒传成功: {ok_instant}")
print(f"  新项目文件落地且字节一致: {ok_new} ({path_new})")
print(f"  被引用项目 208 的源文件仍在: {ok_208}")

print("\n=== 3. 新项目秒传后能跑 ===")
st, d = api("POST", f"/projects/{pid}/submit", {}, token=dev)
st, d = api("GET", "/admin/reviews/queue", token=admin)
item = next((x for x in ((d.get("data") or {}).get("list") or []) if x.get("project_id") == pid), None)
if item:
    api("POST", f"/admin/reviews/{item['latest_version_id']}/approve", {"reason": "秒传复验通过"}, token=admin)
st, d = api("GET", f"/projects/{pid}/run")
url = (d.get("data") or {}).get("url")
r = requests.get(url, timeout=60)
print(f"  run -> [{r.status_code}] {len(r.content)} bytes, turbowarp={b'packager.turbowarp.org' in r.content[:400]}")

print("\n=== 4. 收尾：删掉复验项目（KEEP 208 作为演示）===")
st, d = api("DELETE", f"/admin/projects/{pid}", token=admin)
print(f"  delete {pid} -> {st} {d.get('code')} {d.get('message')}")

print("\n结论：秒传不再破坏源项目 =", ok_instant and ok_208 and ok_new)
