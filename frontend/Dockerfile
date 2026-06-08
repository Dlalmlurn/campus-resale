# 开发阶段：Vite dev server 读取挂载源码，前端改动自动热更新。
FROM docker.1ms.run/library/node:20-alpine AS dev
WORKDIR /workspace
COPY package*.json ./
RUN npm ci
EXPOSE 5173
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0", "--port", "5173"]

# 阶段一：编译前端
FROM docker.1ms.run/library/node:20-alpine AS build
WORKDIR /workspace
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# 阶段二：Nginx 只提供容器内静态 SPA；NAS 公网 TLS、4342/4343 入口和 /api,/ws 反代由 Caddy 承担。
FROM docker.1ms.run/library/nginx:1.25-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 80
