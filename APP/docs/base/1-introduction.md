# 简介

<div class="md-center" style="margin-top: 20px;">

![node version](https://img.shields.io/badge/node-%3E%3D18-green)
![pnpm version](https://img.shields.io/badge/pnpm-%3E%3D7.30-green)

</div>

`unibest` 是最好的 `uniapp` 开发框架，由 `uniapp` + `Vue3` + `Ts` + `Vite5` + `UnoCss` + `VSCode`(可选 `webstorm`) + `uni插件`+ `wot-ui`（可选其他 UI 库）构建，集成了多种工具和技术，使用了最新的前端技术栈，无需依靠 `HBuilderX`，通过命令行方式即可运行 `web`、`小程序` 和 `App`。（注：`App` 还是需要 `HBuilderX`）

`unibest` 内置了 `约定式路由`、`layout布局`、`请求封装`、`请求拦截`、`登录拦截`、`UnoCSS`、`i18n多语言` 等基础功能，提供了 `代码提示`、`自动格式化`、`统一配置`、`代码片段` 等辅助功能，让你编写 `uniapp` 拥有 `best` 体验 （ `unibest 的由来`）。

> `unibest` 目前支持 `H5`、`小程序` 和 `App`。

## 🗂 生成过程

`unibest` 由最初始的官方 cli 脚手架模板生成，执行的命令如下：

```sh
npx degit dcloudio/uni-preset-vue#vite-ts my-vue3-project
```

`uniapp` 官方链接：[点击跳转 - quickstart-cli](https://uniapp.dcloud.net.cn/quickstart-cli.html)

在官方生成的项目基础上，增加了如下内容：

- 前端基础配置六件套
  - prettier
  - eslint
  - stylelint
  - husky
  - lint-staged
  - commitlint
- UnoCSS
- UnoCSS Icons
- Uni 插件
  - vite-plugin-uni-pages
  - vite-plugin-uni-layouts
  - vite-plugin-uni-manifest
  - vite-plugin-uni-platform
- UI 库（默认 `wot-ui`，支持替换其他 `UI库`)
- pinia + pinia-plugin-persistedstate
- 通用功能
  - 请求封装
  - 请求拦截
  - 路由拦截

## ✨ 特性

- ⚡️ [Vue 3](https://github.com/vuejs/core), [Vite](https://github.com/vitejs/vite), [pnpm](https://pnpm.io/), [esbuild](https://github.com/evanw/esbuild) - 就是快！

- 🔥 最新语法 - `<script lang="ts" setup>` 语法

- 🎨 [UnoCSS](https://unocss.dev/) - 高性能且极具灵活性的即时原子化 CSS 引擎

- 😃 [UnoCSS Icons](https://unocss.dev/presets/icons) & [icones](https://icones.js.org/) - 海量图标供你选择

- 🍍 [pinia](https://pinia.vuejs.org/) & [pinia-plugin-persistedstate](https://prazdevs.github.io/pinia-plugin-persistedstate/zh/guide/) - 全端适配的全局数据管理

- 🗂 `uni.request` 请求封装 - 一键引入，快捷使用

- 📦 `路由拦截` 基础封装，支持扩展，快捷使用，拒绝黑盒

- 📥 [API 自动加载](https://github.com/antfu/unplugin-auto-import) - 直接使用 Composition API 无需引入

- 🎉 `v3` Code Snippets 加快你的页面生成

- 🦾 `Pritter` & `ESLint` & `Stylelint` & `husky` & `lint-staged` + `commitlint` - 保证代码质量

- 🌈 `TypeScript` 加持，同时又兼容 `js` ，同时满足不同人群

- 💡 `ES6 import` 自动排序，`css 属性` 自动排序，增强编码一致性

- 🖥 `多环境` 配置分开，想则怎么配置就怎么配置

## 📦 目录结构

通过 `tree -I node_modules -I dist -I .git -a > tree.md` 命令生成。

```txt
.
├── .editorconfig
├── .eslintignore
├── .eslintrc-auto-import.json
├── .eslintrc.cjs
├── .gitignore
├── .husky
├── .npmrc
├── .prettierignore
├── .prettierrc.cjs
├── .stylelintignore
├── .stylelintrc.cjs
├── .vscode
├── LICENSE
├── README.md
├── commitlint.config.cjs
├── env
│   ├── .env
│   ├── .env.development
│   ├── .env.production
│   └── .env.test
├── favicon.ico
├── index.html
├── manifest.config.ts
├── package.json
├── pages.config.ts
├── src
│   ├── App.vue
│   ├── components
│   │   └── .gitkeep
│   ├── env.d.ts
│   ├── hooks
│   │   ├── .gitkeep
│   │   ├── useRequest.ts
│   │   └── useUpload.ts
│   ├── interceptors
│   │   ├── index.ts
│   │   ├── prototype.ts
│   │   ├── request.ts
│   │   └── route.ts
│   ├── layouts
│   │   ├── default.vue
│   │   └── demo.vue
│   ├── main.ts
│   ├── manifest.json
│   ├── pages
│   │   ├── about
│   │   │   ├── about.vue
│   │   │   └── components
│   │   │   ├── request.vue
│   │   │   └── upload.vue
│   │   └── index
│   │   └── index.vue
│   ├── pages-sub
│   │   └── demo
│   │   └── index.vue
│   ├── pages.json
│   ├── service
│   │   └── index
│   │   └── foo.ts
│   ├── static
│   │   ├── images
│   │   │   └── .gitkeep
│   │   ├── logo.svg
│   │   └── tabbar
│   │   ├── example.png
│   │   ├── exampleHL.png
│   │   ├── home.png
│   │   ├── homeHL.png
│   │   ├── personal.png
│   │   └── personalHL.png
│   ├── store
│   │   ├── index.ts
│   │   └── user.ts
│   ├── style
│   │   └── index.scss
│   ├── types
│   │   ├── auto-import.d.ts
│   │   ├── global.d.ts
│   │   ├── shims-uni.d.ts
│   │   └── uni-pages.d.ts
│   ├── typings.ts
│   ├── uni.scss
│   ├── uni_modules
│   │   └── .gitkeep
│   └── utils
│   ├── http.ts
│   ├── index.ts
│   └── platform.ts
├── tsconfig.json
├── uni-pages.d.ts
├── uno.config.ts
└── vite.config.ts
```
