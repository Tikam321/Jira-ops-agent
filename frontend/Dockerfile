# ===========================================
# Stage 1: Build the React application
# ===========================================
FROM node:20-alpine AS builder

WORKDIR /app

# Build arguments for environment
ARG VITE_API_BASE_URL=/api
ARG VITE_FRONTEND_URL=http://localhost:5173
ARG BACKEND_URL=http://backend:8081

# Set environment variables for Vite build
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_FRONTEND_URL=$VITE_FRONTEND_URL

# Copy package files
COPY package*.json ./

# Install dependencies
RUN npm ci

# Copy source code
COPY . .

# Build the application
RUN npm run build

# ===========================================
# Stage 2: Prepare nginx config
# ===========================================
FROM alpine:3.19 AS nginx-config

ARG BACKEND_URL=http://backend:8081

RUN apk add --no-cache gettext

WORKDIR /build
COPY nginx.conf.template .

RUN mkdir -p /etc/nginx/conf.d && \
    envsubst '${BACKEND_URL}' < nginx.conf.template > /etc/nginx/conf.d/default.conf

# ===========================================
# Stage 3: Serve with nginx
# ===========================================
FROM nginx:alpine

# Copy pre-generated nginx config
COPY --from=nginx-config /etc/nginx/conf.d/default.conf /etc/nginx/conf.d/default.conf

# Copy built assets from builder
COPY --from=builder /app/dist /usr/share/nginx/html

# Expose port
EXPOSE 80

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
