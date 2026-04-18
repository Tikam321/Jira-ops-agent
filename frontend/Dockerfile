# ===========================================
# Stage 1: Build the React application
# ===========================================
FROM node:20-alpine AS builder

WORKDIR /app

# Build arguments for environment
ARG VITE_API_BASE_URL
ARG VITE_FRONTEND_URL

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
# Stage 2: Serve with nginx
# ===========================================
FROM nginx:alpine

# Copy the simplified nginx config
COPY nginx.conf.template /etc/nginx/conf.d/default.conf

# Copy built assets from builder
COPY --from=builder /app/dist /usr/share/nginx/html

# Expose port
EXPOSE 80

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
