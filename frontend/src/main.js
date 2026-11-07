import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import vi from 'element-plus/es/locale/lang/vi'
import 'element-plus/dist/index.css'
import './style.css'
import App from './App.vue'
import router from './router'

createApp(App)
  .use(createPinia())
  .use(router)
  .use(ElementPlus, { locale: vi })
  .mount('#app')
