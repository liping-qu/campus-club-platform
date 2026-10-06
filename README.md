# 校园社团通

基于 uni-app + Vue 3 + TypeScript 的校园社团管理平台第一阶段原型。

## 当前完成

- 五个底部 Tab：首页、社团、活动、消息、我的
- 清爽校园风主题和响应式卡片布局
- 本地社团、活动、消息假数据
- H5 / 微信小程序基础页面结构

## 运行

```bash
npm install
npm run dev:h5
```

在 HBuilderX 中打开本目录，也可以直接运行到微信开发者工具。

## 微信小程序预览

1. 双击 `build-wechat.bat` 生成小程序代码。
2. 打开微信开发者工具，扫码登录。
3. 导入目录 `dist\\build\\mp-weixin`。
4. 有真实 AppID 时填写真实 AppID；没有时可先使用测试号或游客模式预览。

源代码目录是项目根目录，微信开发者工具导入的是构建后的 `dist\\build\\mp-weixin` 目录。
