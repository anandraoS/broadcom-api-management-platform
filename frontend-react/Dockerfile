# docker build -t api-platform/frontend-react:1.0.0 frontend-react
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# unprivileged nginx (uid 101, port 8080); same config as the Angular image: same-origin /api/ proxy, CSP, security headers
FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s CMD wget -qO- http://localhost:8080/healthz >/dev/null || exit 1
