import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import authService from '../services/authService';

const EmptyWorkspace = {
  template: '<div></div>',
};

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Main',
    component: () => import('../views/MainView.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'Friends',
        component: () => import('../components/FriendsHome.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: '/direct/:conversationId',
        name: 'DirectConversation',
        component: () => import('../components/DirectMessageView.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: '/servers/:serverId',
        name: 'Server',
        component: EmptyWorkspace,
        meta: { requiresAuth: true },
      },
      {
        path: '/servers/:serverId/channels/:channelId',
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
    path: '/invite/:code',
    name: 'Invite',
    component: () => import('../views/InviteView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/登录',
    redirect: '/login',
  },
  {
    path: '/注册',
    redirect: '/register',
  },
  {
    path: '/服务器/:serverId/频道/:channelId',
    redirect: (to) => `/servers/${to.params.serverId}/channels/${to.params.channelId}`,
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
