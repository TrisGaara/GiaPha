import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from './stores/auth'

const routes = [
  { path: '/', redirect: '/families' },
  { path: '/login', component: () => import('./views/LoginView.vue'), meta: { public: true } },
  { path: '/register', component: () => import('./views/RegisterView.vue'), meta: { public: true } },
  { path: '/families', component: () => import('./views/FamiliesView.vue') },
  { path: '/families/:id', component: () => import('./views/FamilyView.vue'), props: true },
  { path: '/join', component: () => import('./views/JoinView.vue') },
  { path: '/notifications', component: () => import('./views/NotificationsView.vue') },
  { path: '/profile', component: () => import('./views/ProfileView.vue') },
  { path: '/admin', component: () => import('./views/AdminView.vue'), meta: { superAdmin: true } },
  { path: '/:pathMatch(.*)*', redirect: '/families' }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.loggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.superAdmin && !auth.isSuperAdmin) {
    return '/families'
  }
})

export default router
