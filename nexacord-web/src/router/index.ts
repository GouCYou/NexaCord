import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import authService from '../services/authService';

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Main',
    component: () => import('../views/MainView.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '/server/:serverId/channel/:channelId',
        name: 'Channel',
        component: () => import('../components/MessageList.vue'),
        meta: { requiresAuth: true },
      },
    ],
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/LoginView.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/RegisterView.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/',
  },
];

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
});

router.beforeEach((to, _from, next) => {
  const isAuthenticated = authService.isAuthenticated();
  const requiresAuth = Boolean(to.meta.requiresAuth);

  if (requiresAuth && !isAuthenticated) {
    next({
      path: '/login',
      query: {
        redirect: to.fullPath,
      },
    });
    return;
  }

  if ((to.name === 'Login' || to.name === 'Register') && isAuthenticated) {
    next('/');
    return;
  }

  next();
});

export default router;
