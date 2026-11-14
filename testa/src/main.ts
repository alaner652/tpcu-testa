import { createApp } from 'vue'
import App from './App.vue'
import { router } from './router'
// Fonts are bundled, with no external CDN (lab networks may have no internet). Chinese uses the system sans-serif.
import '@fontsource/ibm-plex-sans/400.css'
import '@fontsource/ibm-plex-sans/500.css'
import '@fontsource/ibm-plex-sans/600.css'
import '@fontsource/ibm-plex-mono/400.css'
import '@fontsource/ibm-plex-mono/500.css'
import './style.css'
import 'vue-sonner/style.css'

createApp(App).use(router).mount('#app')
