import http, { safe } from './http'
/* ================= 3.1 认证 ================= */
export const authApi = {
  devApply: (d) => http.post('/auth/dev-apply', d),
  login: (d) => http.post('/auth/login', d, { skipAuthRedirect: true }),
  me: () => http.get('/auth/me'),
  changePassword: (d) => http.post('/auth/change-password', d),
  cancelAccount: (d) => http.post('/auth/cancel-account', d),
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
  readReports: () => http.post('/reports/me/read'),
  /** Bug 反馈（设置页提交） */
  bugFeedback: (d) => http.post('/bug-feedback', d)
}

/* ================= 3.4 投稿与文件 ================= */
export const uploadApi = {
  createProject: (d) => http.post('/projects', d, { timeout: 60000 }),
  updateProject: (id, d) => http.put(`/projects/${id}`, d),
  init: (d) => http.post('/uploads/init', d),
  putPart: (sid, form) =>
    http.post(`/uploads/${sid}/parts`, form, {
      timeout: 0,
    }),
  session: (sid) => http.get(`/uploads/${sid}`),
  complete: (sid, d) => http.post(`/uploads/${sid}/complete`, d),
  submit: (pid) => http.post(`/projects/${pid}/submit`),
  myProjects: (params) => http.get('/users/me/projects', { params }),
  fileContent: (fid) => http.get(`/files/${fid}/content`, { responseType: 'blob' }),
  buildRetry: (pid) => http.post(`/projects/${pid}/build/retry`),
  /** 上传/更换项目封面（multipart） */
  uploadCover: (pid, formData) => http.post(`/projects/${pid}/cover`, formData, {
  }),
  /** 移除项目封面 */
  removeCover: (pid) => http.delete(`/projects/${pid}/cover`),
  /* ---- 作者自助管理（我的作品页完整增删改查） ---- */
  /** 改标题/简介/描述/分类/标签 */
  updateMine: (pid, d) => http.put(`/projects/${pid}/mine`, d),
  /** 下架 / 恢复上架 / 删除（软删） */
  downMine: (pid, d) => http.post(`/projects/${pid}/down`, d || {}),
  restoreMine: (pid) => http.post(`/projects/${pid}/restore`),
  deleteMine: (pid, d) => http.delete(`/projects/${pid}`, { data: d || {} }),
  /** 把已有作品加入系列（series_id 或 new_series_name + is_public） */
  attachSeries: (pid, d) => http.post(`/projects/${pid}/series`, d),
  /** 作者在线查看 / 保存源码 */
  mySourceFiles: (pid) => http.get(`/projects/${pid}/source-files`),
  saveMySource: (pid, d) => http.put(`/projects/${pid}/source-files`, d)
}

/* ================= 3.5 私信 / 项目群 ================= */
export const imApi = {
  conversations: () => http.get('/im/conversations'),
  // 打开/创建与某人的单聊会话，body: { user_id }
  openDirect: (d) => http.post('/im/conversations/direct', d || {}),
  messages: (sid, params) => http.get(`/im/conversations/${sid}/messages`, { params }),
  send: (sid, d) => http.post(`/im/conversations/${sid}/messages`, d),
  read: (sid) => http.post(`/im/conversations/${sid}/read`, null, { silent: true }),
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
  removeProject: (pid, d) => http.delete(`/admin/projects/${pid}`, { params: { reason: d?.reason } }),
  undeleteProject: (pid) => http.post(`/admin/projects/${pid}/undelete`),
  removeComment: (cid, d) => http.delete(`/admin/comments/${cid}`, { data: d || {} }),
  devApplications: (params) => http.get('/admin/developer-applications', { params }),
  approveDev: (id) => http.post(`/admin/developer-applications/${id}/approve`),
  rejectDev: (id, d) => http.post(`/admin/developer-applications/${id}/reject`, d || {}),
  reports: (params) => http.get('/admin/reports', { params }),
  handleReport: (id, d) => http.post(`/admin/reports/${id}/handle`, d),
  passwordResets: (params) => http.get('/admin/password-resets', { params }),
  resetPassword: (uid, d) => http.post(`/admin/users/${uid}/reset-password`, d || {}),
  rejectPasswordReset: (id, d) => http.post(`/admin/password-resets/${id}/reject`, d || {}),
  cancelUser: (uid, d) => http.post(`/admin/users/${uid}/cancel`, d || {}),
  setAdmin: (uid) => http.post(`/admin/users/${uid}/set-admin`),
  revokeAdmin: (uid) => http.post(`/admin/users/${uid}/revoke-admin`),
  platformStats: () => http.get('/admin/stats/platform'),
  /** 全平台浏览 / 评论趋势折线：range=2/7/15/30 */
  platformTrend: (range) => http.get('/admin/stats/platform/trend', { params: { range } }),
  /** 项目源码文件清单（文本附带内容） */
  sourceFiles: (pid) => http.get(`/admin/projects/${pid}/source-files`),
  /** 在线保存源码文本文件修改 */
  saveSource: (pid, d) => http.put(`/admin/projects/${pid}/source-files`, d),
  bugFeedbacks: (params) => http.get('/admin/bug-feedback', { params }),
  bugFeedbackDetail: (id) => http.get(`/admin/bug-feedback/${id}`),
  addBugFeedback: (d) => http.post('/admin/bug-feedback', d),
  updateBugFeedback: (id, d) => http.put(`/admin/bug-feedback/${id}`, d),
  deleteBugFeedback: (id) => http.delete(`/admin/bug-feedback/${id}`),
  handleBugFeedback: (id) => http.post(`/admin/bug-feedback/${id}/handle`)
}

/* 评论管理没有独立 GET，借 admin/projects 列表进入后再取详情评论；
   这里给一个便捷入口（后端若没实现会优雅失败）。 */
export const commentsApi = {
  all: (params) => http.get('/admin/comments', { params, silent: true })
}

/* ================= 3.7 系列 / 素材（五期） ================= */
/**
 * 系列：kind = 'project' 项目系列 / 'asset' 素材系列。
 * 两者结构一致（id + 用户 + 名称 + 简介 + 公开性），项目系列只装项目，素材系列只装素材。
 */
export const seriesApi = {
  mine: (kind = 'project') => http.get('/series', { params: { kind } }),
  /** 某人公开的系列（个人主页） */
  publicOf: (userId, kind = 'project') => http.get('/series/public', { params: { user_id: userId, kind }, silent: true }),
  create: (d) => http.post('/series', d),
  update: (id, d) => http.put(`/series/${id}`, d),
  remove: (id, kind = 'project') => http.delete(`/series/${id}`, { params: { kind } })
}

/**
 * 素材：图片 / 动图 / 视频 / 音频 / JSON。
 * 上传复用分片链路：initUpload → putPart（走 uploadApi.putPart）→ completeUpload。
 */
export const assetApi = {
  /** 素材广场（公开） */
  list: (params) => http.get('/assets', { params }),
  /** 我的素材（含未公开） */
  mine: (params) => http.get('/assets/mine', { params }),
  detail: (id) => http.get(`/assets/${id}`),
  initUpload: (d) => http.post('/assets/upload/init', d),
  completeUpload: (sid, d) => http.post(`/assets/upload/${sid}/complete`, d),
  update: (id, d) => http.put(`/assets/${id}`, d),
  remove: (id) => http.delete(`/assets/${id}`)
}

/* 复用导出：api base / ws base / zone / token */
export { safe, API_BASE, WS_BASE, ZONE, IS_USER_ZONE, ORIGIN, getToken, setToken } from './http'
