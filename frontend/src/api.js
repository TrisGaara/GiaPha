import axios from 'axios'
import { ElMessage } from 'element-plus'

const api = axios.create({ baseURL: '/api', timeout: 30000 })

/**
 * "Che do nguoi xem": khi bat, moi request DOC (GET) gui kem header X-View-As de backend tra ve
 * du lieu y het nhu mot tai khoan Khach thay (backend chi ha quyen, khong bao gio nang quyen).
 * Request co cfg.skipViewAs = true (vd: thong tin dong ho de biet quyen that) se khong bi ha quyen.
 */
let viewAs = null
export function setViewAs(value) {
  viewAs = value || null
}

api.interceptors.request.use((cfg) => {
  const token = localStorage.getItem('token')
  if (token) cfg.headers.Authorization = `Bearer ${token}`
  if (viewAs && (cfg.method || 'get') === 'get' && !cfg.skipViewAs) cfg.headers['X-View-As'] = viewAs
  return cfg
})

api.interceptors.response.use(
  (res) => res.data,
  (err) => {
    const status = err.response?.status
    const message = err.response?.data?.message
    if (status === 401 && !err.config.url.startsWith('/auth/login')) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      if (!location.pathname.startsWith('/login')) {
        location.href = '/login?redirect=' + encodeURIComponent(location.pathname + location.search)
      }
    } else {
      ElMessage.error(message || (status === 403 ? 'Bạn không có quyền thực hiện thao tác này' : 'Có lỗi xảy ra, vui lòng thử lại'))
    }
    return Promise.reject(err)
  }
)

export default api
