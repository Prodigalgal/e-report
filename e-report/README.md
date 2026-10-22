# Web 管理端

网络运维管理系统的 React + Vite 前端。生产 WEB 使用根路径 `/`，后端接口使用 `/api`，Android 安装包固定入口为 `/download/android`。

```powershell
npm install
npm test
npm run lint
npm run build
```

本地开发服务器监听 `5173`，并将 `/api` 请求代理到 `localhost:8083`。生产静态文件由仓库内 `nginx.conf` 提供，SPA、PWA 与静态资源均直接使用根路径。
