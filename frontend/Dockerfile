# syntax=docker/dockerfile:1
# ============================================
# DevFlow 前端镜像（Next.js 16 standalone）
# ============================================
# 需要 next.config.ts 中开启 output: 'standalone'，否则运行层找不到 server.js。

# ---------- 依赖 ----------
FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

# ---------- 构建 ----------
FROM node:22-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .

# 构建期需要后端地址（浏览器侧实际用的地址，不是容器内地址）
ARG NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api/v1
ARG NEXT_PUBLIC_AI_BASE_URL=http://localhost:8000/api/v1
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL \
    NEXT_PUBLIC_AI_BASE_URL=$NEXT_PUBLIC_AI_BASE_URL \
    NEXT_TELEMETRY_DISABLED=1

RUN npm run build

# ---------- 运行 ----------
FROM node:22-alpine AS runner
WORKDIR /app

ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0 \
    TZ=Asia/Shanghai

RUN addgroup -S nodejs -g 1001 && adduser -S nextjs -u 1001
USER nextjs

# standalone 产物已包含最小 node_modules
COPY --from=build --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=build --chown=nextjs:nodejs /app/.next/static ./.next/static
COPY --from=build --chown=nextjs:nodejs /app/public ./public

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -qO- http://127.0.0.1:3000/ || exit 1

CMD ["node", "server.js"]
