import { defineStore } from 'pinia'
import api from '../api'

function loadUser() {
  try {
    return JSON.parse(localStorage.getItem('user') || 'null')
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token'),
    user: loadUser()
  }),
  getters: {
    loggedIn: (s) => !!s.token,
    isSuperAdmin: (s) => s.user?.systemRole === 'SUPER_ADMIN'
  },
  actions: {
    setSession({ token, user }) {
      this.token = token
      this.user = user
      localStorage.setItem('token', token)
      localStorage.setItem('user', JSON.stringify(user))
    },
    async login(username, password) {
      this.setSession(await api.post('/auth/login', { username, password }))
    },
    async register(form) {
      this.setSession(await api.post('/auth/register', form))
    },
    async refresh() {
      this.user = await api.get('/auth/me')
      localStorage.setItem('user', JSON.stringify(this.user))
    },
    logout() {
      this.token = null
      this.user = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    }
  }
})
