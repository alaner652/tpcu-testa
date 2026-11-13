import { createApp } from 'vue'
import App from './App.vue'
import { router } from './router'
// 字型跟著前端一起打包，不依賴外部 CDN（教室區網可能沒有對外網路）。中文使用系統內建的黑體。
import '@fontsource/ibm-plex-sans/400.css'
import '@fontsource/ibm-plex-sans/500.css'
import '@fontsource/ibm-plex-sans/600.css'
import '@fontsource/ibm-plex-mono/400.css'
import '@fontsource/ibm-plex-mono/500.css'
import './style.css'
import 'vue-sonner/style.css'

createApp(App).use(router).mount('#app')
