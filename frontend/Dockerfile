FROM node:24-alpine AS build
WORKDIR /build
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
# 빈 base URL은 브라우저의 현재 origin을 사용해 공개 API를 한 주소로 묶는다.
ENV VITE_API_BASE_URL=""
RUN npm run build

FROM caddy:2-alpine
COPY infra/deploy/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /build/dist /srv
