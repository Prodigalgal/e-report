ARG NODE_BASE_IMAGE=docker.m.daocloud.io/library/node:24.19.0-alpine3.24@sha256:d32cdf619f63fe0471182d08996dd516c6275bb5fd31ae06e55a570bd9e1ad43
ARG NGINX_BASE_IMAGE=docker.m.daocloud.io/library/nginx:1.30.4-alpine3.24@sha256:dc5069ad14f19660b141b21236140b91656bf89bbc3e2417c70ae650cd66104c
ARG NPM_VERSION=12.0.2
ARG NPM_TARBALL_SHA512=b885e890b9418fa1693544d05f53e64f9a73ec194837d4258b15fecdd692347b1dd2a517b1b0cbaf9d31cd8e92c3b70956bd2ecc72833a57b4b3098f5bfa7943
ARG NODE_TAR_VERSION=7.5.21
ARG NODE_TAR_TARBALL_SHA512=5dd86d0af94ccb0c31a425bc604ab794e5c126950f4d1d8e1c77302cf3b71f0b09a8e1dad8e93fa09eebb86ce9f89acaa113d50b327001d123a8b5bfbcd44f1c

# ---- 第一阶段：构建 React 应用 ----
FROM ${NODE_BASE_IMAGE} AS builder
ARG NPM_VERSION
ARG NPM_TARBALL_SHA512
ARG NODE_TAR_VERSION
ARG NODE_TAR_TARBALL_SHA512
RUN apk upgrade --no-cache
WORKDIR /app
RUN wget -q -O /tmp/npm.tgz "https://registry.npmjs.org/npm/-/npm-${NPM_VERSION}.tgz" \
    && echo "${NPM_TARBALL_SHA512}  /tmp/npm.tgz" | sha512sum -c - \
    && npm install --global --ignore-scripts /tmp/npm.tgz \
    && wget -q -O /tmp/node-tar.tgz "https://registry.npmjs.org/tar/-/tar-${NODE_TAR_VERSION}.tgz" \
    && echo "${NODE_TAR_TARBALL_SHA512}  /tmp/node-tar.tgz" | sha512sum -c - \
    && npm_root="$(npm root --global)" \
    && rm -rf "${npm_root}/npm/node_modules/tar" \
    && mkdir -p "${npm_root}/npm/node_modules/tar" \
    && tar -xzf /tmp/node-tar.tgz -C "${npm_root}/npm/node_modules/tar" --strip-components=1 \
    && test "$(node -p "require('${npm_root}/npm/node_modules/tar/package.json').version")" = "${NODE_TAR_VERSION}" \
    && rm -f /tmp/npm.tgz /tmp/node-tar.tgz \
    && npm cache clean --force
COPY e-report/package*.json ./
RUN npm config set registry https://registry.npmjs.org \
    && npm ci --ignore-scripts --fetch-retries=5 --fetch-retry-mintimeout=20000 --fetch-retry-maxtimeout=120000
COPY e-report/ .
COPY shared/ /shared/
RUN npm run build

# ---- 第二阶段：使用 Nginx 服务静态文件 ----
FROM ${NGINX_BASE_IMAGE}
RUN apk upgrade --no-cache
RUN rm -rf /usr/share/nginx/html/*
COPY --from=builder /app/dist /usr/share/nginx/html
COPY e-report/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
