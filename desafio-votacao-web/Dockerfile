FROM node:24.15.0-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.28-alpine
ENV PORT=80 \
    API_SCHEME=http \
    API_HOST=host.docker.internal \
    API_PORT=8080
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/desafio-votacao-web/browser /usr/share/nginx/html
EXPOSE 80 10000
HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=3 CMD wget -q -O /dev/null "http://127.0.0.1:${PORT}/" || exit 1
