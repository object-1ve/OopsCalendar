# 多阶段:pnpm 构建静态资源,Nginx 托管并反代 /api
ARG DOCKER_HUB=docker.m.daocloud.io
FROM ${DOCKER_HUB}/library/node:22-alpine AS builder

WORKDIR /src

ARG NPM_REGISTRY=https://registry.npmmirror.com
ENV npm_config_registry=${NPM_REGISTRY} \
    COREPACK_NPM_REGISTRY=${NPM_REGISTRY}

RUN corepack enable

COPY frontend/package.json frontend/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY frontend/ ./
RUN pnpm build

ARG DOCKER_HUB=docker.m.daocloud.io
FROM ${DOCKER_HUB}/library/nginx:1.27-alpine

COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /src/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1
