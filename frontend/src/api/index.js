import http, { safe } from './http'
/* ================= 3.1 认证 ================= */
export const authApi = {
  devApply: (d) => http.post('/auth/dev-apply', d),
  login: (d) => http.post('/auth/login', d),
  me: () => http.get('/auth/me'),
  changePassword: (d) => http.post('/auth/change-password', d),
  forgot: (d) => http.post('/auth/password/forgot', d)
}

/* ================= 3.2 内容（访客可读） ================= */
export const projectApi = {
  list: (params) => http.get('/projects', { params }),
  detail: (id, cfg = {}) => http.get(`/projects/${id}`, cfg),
  view: (id) => http.post(`/projects/${id}/view`, null, { silent: true }),
  stats: (id, range) => http.get(`/projects/${id}/stats`, { params: { range } }),
  run: (id) => http.get(`/projects/${id}/run`, { silent: true }),
  comments: (id, params) => http.get(`/projects/${id}/comments`, { params })
}

export const categoryApi = {
  list: () => http.get('/categories')
}

export const userApi = {
  profile: (id) => http.get(`/users/${id}`),
  followStatus: (id) => http.get(`/users/${id}/follow-status`, { silent: true })
}

export const systemApi = {
  health: () => http.get('/system/health', { silent: true })
}

/* ================= 3.3 互动（需登录） ================= */
export const interactApi = {
  comment: (d) => http.post('/comments', d),
  likeComment: (cid) => http.post(`/comments/${cid}/like`),
  likeProject: (pid) => http.post(`/projects/${pid}/like`),
  favoriteProject: (pid) => http.post(`/projects/${pid}/favorite`),
  follow: (uid) => http.post(`/users/${uid}/follow`),
  report: (d) => http.post('/reports', d),
  myReports: (params) => http.get('/reports/me', { params }),
  readReports: () => http.post('/reports/me/read')
}

/* ================= 3.4 投稿与文件 ================= */
export const uploadApi = {
  createProject: (d) => http.post('/projects', d),
  updateProject: (id, d) => http.put(`/projects/${id}`, d),
  init: (d) => http.post('/uploads/init', d),
  putPart: (sid, form) =>
    http.post(`/uploads/${sid}/parts`, form, {
      timeout: 0,
      headers: { 'Content-Type': 'multipart/form-data' }
    }),
  session: (sid) => http.get(`/uploads/${sid}`),
  complete: (sid, d) => http.post(`/uploads/${sid}/complete`, d),
  submit: (pid) => http.post(`/projects/${pid}/submit`),
  myProjects: (params) => http.get('/users/me/projects', { params }),
  fileContent: (fid) => http.get(`/files/${fid}/content`, { responseType: 'blob' }),
  buildRetry: (pid) => http.post(`/projects/${pid}/build/retry`)
}

/* ================= 3.5 私信 / 项目群 ================= */
export const imApi = {
  conversations: () => http.get('/im/conversations'),
  messages: (kind, id, params) => http.get(`/im/conversations/${kind}/${id}/messages`, { params }),
  send: (kind, id, d) => http.post(`/im/conversations/${kind}/${id}/messages`, d),
  read: (kind, id) => http.post(`/im/conversations/${kind}/${id}/read`, null, { silent: true }),
  unread: () => http.get('/im/unread', { silent: true })
}

/* ================= 3.6 管理端 ================= */
export const adminApi = {
  projects: (params) => http.get('/admin/projects', { params }),
  storage: (pid) => http.get(`/admin/projects/${pid}/storage`),
  approve: (vid, d) => http.post(`/admin/reviews/${vid}/approve`, d || {}),
  reject: (vid, d) => http.post(`/admin/reviews/${vid}/reject`, d || {}),
  down: (pid, d) => http.post(`/admin/projects/${pid}/down`, d || {}),
  restore: (pid) => http.post(`/admin/projects/${pid}/restore`),
  removeProject: (pid, d) => http.delete(`/admin/projects/${pid}`, { data: d || {} }),
  removeComment: (cid, d) => http.delete(`/admin/comments/${cid}`, { data: d || {} }),
  devApplications: (params) => http.get('/admin/developer-applications', { params }),
  approveDev: (id) => http.post(`/admin/developer-applications/${id}/approve`),
  rejectDev: (id, d) => http.post(`/admin/developer-applications/${id}/reject`, d || {}),
  reports: (params) => http.get('/admin/reports', { params }),
  handleReport: (id, d) => http.post(`/admin/reports/${id}/handle`, d),
  passwordResets: (params) => http.get('/admin/password-resets', { params }),
  resetPassword: (uid) => http.post(`/admin/users/${uid}/reset-password`),
  cancelUser: (uid, d) => http.post(`/admin/users/${uid}/cancel`, d || {}),
  platformStats: () => http.get('/admin/stats/platform')
}

/* 评论管理没有独立 GET，借 admin/projects 列表进入后再取详情评论；
   这里给一个便捷入口（后端若没实现会优雅失败）。 */
export const commentsApi = {
  all: (params) => http.get('/admin/comments', { params, silent: true })
}

/* 复用导出：api base / ws base / zone / token */
export { safe, API_BASE, WS_BASE, ZONE, IS_USER_ZONE, getToken, setToken } from './http'
