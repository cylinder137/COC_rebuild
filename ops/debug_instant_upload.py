# -*- coding: utf-8 -*-
"""定位：同一文件 sha256 秒传 + 挂到另一个项目时 complete 失败的原因。"""
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


st, d = api("POST", "/auth/login", {"qq_account": "10001", "password": "Coc@2026"})
tok = d["data"]["token"]

st, d = api("POST", "/projects", {
    "title": "星球（秒传跨项目测试）",
    "summary": "验证同一 sha256 文件秒传到另一个项目",
    "description": "秒传 + 跨项目复用测试",
    "project_type": "html",
    "category_id": 101,
}, token=tok)
pid = d["data"]["project_id"]
print("新草稿 project_id =", pid, "|", d)

st, d = api("POST", "/uploads/init", {"file_name": fname, "file_size": len(data), "sha256": sha, "chunk_size": 1048576}, token=tok)
print("init ->", st, json.dumps(d, ensure_ascii=False)[:400])
sid = (d.get("data") or {}).get("upload_session_id")

st, d = api("POST", f"/uploads/{sid}/complete", {"project_id": pid, "file_type": "source", "changelog": "秒传复用"},
            token=tok)
print("complete ->", st, json.dumps(d, ensure_ascii=False)[:600])

st, d = api("GET", f"/projects/{pid}/run")
print("run ->", st, json.dumps(d, ensure_ascii=False)[:300])
