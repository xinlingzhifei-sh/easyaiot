import dayjs from 'dayjs'
import { defineConfig } from 'vitepress'
import llmsPlugin from 'vitepress-plugin-llms'
import packageJson from '../../package.json'

const buildTime = dayjs().format('YYYY-MM-DD HH:mm:ss')

const base = '/unibest/' // default is /

// https://vitepress.dev/reference/site-config
export default defineConfig({
  lang: 'zh-CN',
  base,
  title: 'unibest 官方文档',
  description: '最好用的 uniapp 开发模板',
  lastUpdated: true,
  cleanUrls: true,
  head: [
    ['link', { rel: 'icon', href: `${base}favicon.ico` }],
    // 增加构建信息
    ['meta', { name: 'build-time', content: buildTime }],
    ['meta', { name: 'version', content: packageJson.version }],
    [
      'meta',
      {
        name: 'keywords',
        content: 'unibest, uniapp, uni-app, vue, vue3, vite,template, typescript, ts',
      },
    ],
    [
      'meta',
      {
        name: 'twitter:title',
        content: '最好用的 uniapp 开发模板',
      },
    ],
    // 百度联盟meta
    ['meta', { property: 'baidu_union_verify', content: '8ab9e6068e7febf94e684886f81f406f' }],
    // 其他杂七杂八的 meta 标签
    ['meta', { name: 'twitter:card', content: 'summary_large_image' }],
    [
      'meta',
      {
        property: 'og:image:alt',
        content: '最好用的 uniapp 开发模板',
      },
    ],
    ['meta', { property: 'og:image:width', content: '1200' }],
    ['meta', { property: 'og:image:height', content: '600' }],
    ['meta', { property: 'og:site_name', content: 'GitHub' }],
    ['meta', { property: 'og:type', content: 'object' }],
    [
      'meta',
      {
        property: 'og:title',
        content: '最好用的 uniapp 开发模板',
      },
    ],
    [
      'meta',
      {
        property: 'og:description',
        content: '最好用的 uniapp 开发模板',
      },
    ],
    // 下面是百度统计代码
    ['script', { async: '', src: 'https://hm.baidu.com/hm.js?081c2ec121383d9e7d5a35c5833ab6ff' }],
    // 下面是不蒜子统计代码
    ['script', { async: '', src: '//busuanzi.ibruce.info/busuanzi/2.3/busuanzi.pure.mini.js' }],
  ],
  markdown: {
    theme: {
      light: 'vitesse-light',
      dark: 'vitesse-dark',
    },
  },
  themeConfig: {
    // https://vitepress.dev/reference/default-theme-config
    logo: '/logo.png',
    siteTitle: 'unibest',
    nav: [
      {
        text: '快速开始',
        link: '/base/2-start',
        activeMatch: '/base',
      },
      {
        text: '更新日志',
        link: '/changelog/CHANGELOG',
      },
      {
        text: '相关链接',
        link: '/other/links/links',
      },
    ],
    sidebar: [
      {
        text: '基础·必看',
        base: '/base/',
        items: [
          { text: '介绍', link: '1-introduction' },
          {
            text: '快速开始',
            link: '2-start',
          },
          { text: 'uni 插件', link: '3-plugin' },
          { text: '样式篇', link: '4-style' },
          // { text: '样式篇2', link: '4-style2' },
          { text: '图标篇', link: '5-icons' },
          { text: 'SVG篇', link: '6-svg' },
          { text: 'UI库选型篇', link: 'ui/ui' },
          { text: 'UI库替换篇', link: '7-ui' },
          { text: '请求篇', link: '8-request' },
          { text: '状态篇', link: '9-state' },
          { text: '多语言篇', link: '10-i18n' },
          { text: '运行发布', link: '11-build' },
          { text: 'App 专题', link: '18-app' },
          // { text: '环境变量', link: '12-env' },
          { text: 'hbx 模板', link: '13-hbx' },
          { text: '常见问题', link: '14-faq' },
          { text: '常见问题2', link: '15-faq' },
          // { text: '小程序标识', link: '16-terminology' },
          { text: '自动生成代码', link: '17-generate' },
          // { text: '最佳实践', link: '20-best' },
        ],
      },
      {
        text: '⭐ 优秀案例',
        link: '/advanced/showcase/showcase',
      },
      {
        text: '更新日志',
        link: '/changelog/CHANGELOG',
      },
      {
        text: '升级指南',
        link: '/changelog/upgrade',
      },
      {
        text: '延伸',
        base: '/other/',
        items: [
          { text: '相关链接', link: 'links/links' },
          { text: '图片占位图', link: 'image/image' },
          { text: 'iconfont详细版', link: 'iconfont/iconfont' },
          // { text: 'Git 提交优化', link: 'czg/czg' },
          // { text: '文件资源展示优化', link: 'files/files' },
          { text: 'unibest博客', link: 'blog' },
        ],
      },
    ],
    search: {
      provider: 'local',
      options: {
        locales: {
          root: {
            translations: {
              button: {
                buttonText: '搜索文档',
                buttonAriaLabel: '搜索文档',
              },
              modal: {
                displayDetails: '显示详情',
                resetButtonTitle: '清除查询条件',
                backButtonTitle: '返回',
                noResultsText: '无法找到相关结果',
                footer: {
                  selectText: '选择',
                  selectKeyAriaLabel: '选择',
                  navigateText: '切换',
                  navigateUpKeyAriaLabel: '切换',
                  navigateDownKeyAriaLabel: '切换',
                  closeText: '关闭',
                  closeKeyAriaLabel: '关闭',
                },
              },
            },
          },
        },
      },
    },
  },
  vite: {
    // 其他配置...
    plugins: [llmsPlugin() as any],
  },
})
