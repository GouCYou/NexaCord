import { createMemoryHistory, createRouter, type RouteRecordRaw } from 'vue-router';
import authService from '../services/authService';
import DirectMessageView from '../components/DirectMessageView.vue';
import FriendsHome from '../components/FriendsHome.vue';
import MessageList from '../components/MessageList.vue';
import InviteView from '../views/InviteView.vue';
import LoginView from '../views/LoginView.vue';
import MainView from '../views/MainView.vue';
import RegisterView from '../views/RegisterView.vue';

if (window.location.pathname !== '/') {
  window.history.replaceState(window.history.state, '', '/');
}

const EmptyWorkspace = {
  template: '<div></div>',
};

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Main',
    component: MainView,
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'Friends',
        component: FriendsHome,
        meta: { requiresAuth: true },
      },
      {
        path: '/direct/:conversationId',
        name: 'DirectConversation',
        component: DirectMessageView,
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
        component: MessageList,
        meta: { requiresAuth: true },
      },
    ],
  },
  {
    path: '/login',
    name: 'Login',
    component: LoginView,
    meta: { requiresAuth: false },
  },
  {
    path: '/register',
    name: 'Register',
    component: RegisterView,
    meta: { requiresAuth: false },
  },
  {
    path: '/invite/:code',
    name: 'Invite',
    component: InviteView,
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
  history: createMemoryHistory(import.meta.env.BASE_URL),
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
