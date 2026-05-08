import type AvatarImage from '../components/AvatarImage.vue';

declare module 'vue' {
  export interface GlobalComponents {
    AvatarImage: typeof AvatarImage;
  }
}
