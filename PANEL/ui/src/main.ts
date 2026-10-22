import { createApp } from 'vue'
import Antd from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'
import App from './App.vue'
import './styles.css'
import { setupBrandDisplay } from './brandDisplay'

// 页面上显示的容器名、路径、环境变量统一换成 yFeiEye 写法（只改展示，真实值不变）
setupBrandDisplay()

createApp(App).use(Antd).mount('#app')
