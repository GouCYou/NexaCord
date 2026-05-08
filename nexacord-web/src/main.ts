import { createApp } from 'vue';
import { createPinia } from 'pinia';
import 'element-plus/dist/index.css';
import './style.css';
import App from './App.vue';
import AvatarImage from './components/AvatarImage.vue';
import router from './router';
import { useThemeStore } from './stores/themeStore';
import { useUserStore } from './stores/userStore';

const app = createApp(App);
const pinia = createPinia();

app.use(pinia);
app.use(router);
app.component('AvatarImage', AvatarImage);

const userStore = useUserStore();
const themeStore = useThemeStore();
themeStore.initializeTheme();
userStore.initializeUser();

window.addEventListener('nexacord:auth-expired', () => {
  if (router.currentRoute.value.name !== 'Login') {
    void router.push('/login');
  }
});

app.mount('#app');
