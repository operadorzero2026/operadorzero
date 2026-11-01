FROM node:24-alpine@sha256:8a32c8cf649a49915a66e81e7a609223c708d0adabf288ee3f4077d765542f85 AS build

ARG VITE_APP_ENV=production
ARG VITE_AUTH_ENABLED=true
ENV VITE_APP_ENV=$VITE_APP_ENV \
    VITE_AUTH_ENABLED=$VITE_AUTH_ENABLED

WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci
COPY index.html tsconfig.json tsconfig.app.json tsconfig.node.json vite.config.ts ./
COPY public ./public
COPY src ./src
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.28-alpine@sha256:7377697a821c131a924a7105fafbe7414db4e9fcc77a6f08f776f33f141ec3f8

COPY deploy/vps/nginx.conf /etc/nginx/conf.d/default.conf
COPY deploy/vps/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /workspace/dist /usr/share/nginx/html

EXPOSE 8080
