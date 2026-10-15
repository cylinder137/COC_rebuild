# -*- coding: utf-8 -*-
"""收尾清理：删掉重复的「星球」验收项目，只留一个已发布演示。"""
import json
import requests

BASE = "http://127.0.0.1:8090/api/v1"
KEEP = {201, 202, 203, 204, 205, 206}   # 种子演示项目
KEEP_ONE = 214                          # 保留最新一次端到端验收产生的「星球」项目


def api(m, p, body=None, token=None):
    h = {"Authorization": "Bearer " + token} if token else {}
    h["Content-Type"] = "application/json"
    r = requests.request(m, BASE + p, data=json.dumps(body) if body is not None else None, headers=h, timeout=60)
    try:
        return r.status_code, r.json()
    except Exception:
        return r.status_code, {"_raw": r.text[:200]}


st, d = api("POST", "/auth/login", {"qq_account": "3254328344", "password": "Coc@2026"})
admin = d["data"]["token"]

st, d = api("GET", "/admin/projects?page=1&size=100&include_deleted=1", token=admin)
for r in (d.get("data") or {}).get("list") or []:
    pid, title = r.get("project_id"), r.get("title") or ""
    if pid in KEEP or pid == KEEP_ONE:
        continue
    st2, d2 = api("DELETE", f"/admin/projects/{pid}", token=admin)
    print(f"删除 {pid} {title[:26]!r} -> {st2}/{d2.get('code')}")

st, d = api("GET", "/projects?page=1&size=50")
print("\n访客可见项目：")
for p in (d.get("data") or {}).get("list") or []:
    print(f"  #{p.get('project_id')} {p.get('title')} [{p.get('project_type')}] by {p.get('author_nickname')}")

st, d = api("GET", "/admin/stats/platform", token=admin)
print("\n平台统计:", json.dumps(d.get("data"), ensure_ascii=False))
