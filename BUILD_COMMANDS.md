# Build Commands Guide

This document contains all build commands for the Jira Ops Agent application.

---

## Backend (Spring Boot with Jib)

### Prerequisites
- Java 17+
- Gradle 8.14+

### Build Commands

```bash
# Navigate to backend directory
cd Jira-ops-agent

# Clean and build (skip tests)
./gradlew clean build -x test

# Build with tests
./gradlew build

# Build Docker image to local Docker daemon
./gradlew jibDockerBuild

# Build and push Docker image to registry
./gradlew jib

# Build with specific image tag
./gradlew jib --image=docker.io/username/jira-ops-agent:v1.0.0
```

### Docker Image Details

- **Image Name:** `tikam321/jira-ops-agent`
- **Tags:** `latest`, `1.0.0`
- **Base Image:** `eclipse-temurin:17-jre`
- **Size:** ~363MB
- **Port:** 8081
- **Profile:** `prod` (activates `application-prod.yml`)

---

## Frontend (React with Vite + Docker)

### Prerequisites
- Node.js 20+
- Docker (for container build)

### Development Commands

```bash
# Navigate to frontend directory
cd frontend

# Install dependencies
npm install

# Start development server
npm run dev

# Type check
npm run lint

# Build for production
npm run build
```

### Docker Build Commands

The frontend Docker image uses multi-stage build with nginx. Build arguments are used to configure:

| Argument | Purpose |
|---------|---------|
| `VITE_API_BASE_URL` | API endpoint for frontend (used in JS code) |
| `VITE_FRONTEND_URL` | Frontend URL (used in JS code) |
| `BACKEND_URL` | Backend URL for nginx proxy (used in nginx.conf) |

#### Local Testing (with local backend)

```bash
cd frontend

# Build image with local backend URL
docker build -t tikam321/jira-ops-frontend:local \
  --build-arg VITE_API_BASE_URL=/api \
  --build-arg VITE_FRONTEND_URL=http://localhost \
  --build-arg BACKEND_URL=http://localhost:8081 .

# Run container
docker run -p 80:80 tikam321/jira-ops-frontend:local
```

#### Docker Compose Style (with backend container)

```bash
cd frontend

# Build image with backend service name (Docker Compose)
docker build -t tikam321/jira-ops-frontend:compose \
  --build-arg VITE_API_BASE_URL=/api \
  --build-arg VITE_FRONTEND_URL=http://localhost \
  --build-arg BACKEND_URL=http://backend:8081 .
```

#### Production Build (for Render deployment)

```bash
cd frontend

# Build with production URLs
docker build -t tikam321/jira-ops-frontend:latest \
  --build-arg VITE_API_BASE_URL=/api \
  --build-arg VITE_FRONTEND_URL=https://jira-ops-frontend.onrender.com \
  --build-arg BACKEND_URL=https://jira-ops-backend.onrender.com .

# Push to Docker Hub
docker push tikam321/jira-ops-frontend:latest
```

### Push to Docker Hub

```bash
# Push latest tag
docker push tikam321/jira-ops-frontend:latest

# Push with version tag
docker build -t tikam321/jira-ops-frontend:latest \
  --build-arg VITE_API_BASE_URL=/api \
  --build-arg VITE_FRONTEND_URL=https://your-domain.com \
  --build-arg BACKEND_URL=https://backend-domain.com .

docker push tikam321/jira-ops-frontend:latest
docker push tikam321/jira-ops-frontend:1.0.0
```

### Docker Image Details

- **Image Name:** `tikam321/jira-ops-frontend`
- **Base Image:** `nginx:alpine`
- **Multi-Stage:** Node (build) → Alpine (nginx config) → nginx (final)
- **Size:** ~62MB
- **Port:** 80

### How it Works

```
┌─────────────────────────────────────────────────────────┐
│ Stage 1: builder (node:20-alpine)                      │
│ - Installs npm dependencies                              │
│ - Runs 'npm run build' with VITE_* env vars            │
│ - Output: /app/dist (static files)                       │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│ Stage 2: nginx-config (alpine:3.19)                      │
│ - Runs envsubst to replace ${BACKEND_URL}                │
│ - Creates nginx.conf with actual backend URL             │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│ Stage 3: final (nginx:alpine)                           │
│ - Copies nginx.conf from stage 2                         │
│ - Copies static files from stage 1                       │
│ - Starts nginx                                          │
└─────────────────────────────────────────────────────────┘
```

---

## Production Deployment

### Required Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `JIRA_OAUTH_CLIENT_ID` | Yes | Jira OAuth Client ID |
| `JIRA_OAUTH_CLIENT_SECRET` | Yes | Jira OAuth Client Secret |
| `GROQ_API_KEY` | Yes | Groq API Key |
| `FRONTEND_URL` | Yes | Production frontend URL |
| `SPRING_DATASOURCE_URL` | Yes | PostgreSQL connection URL |
| `SPRING_DATASOURCE_USERNAME` | Yes | Database username |
| `SPRING_DATASOURCE_PASSWORD` | Yes | Database password |
| `SPRING_PROFILES_ACTIVE` | Yes | Set to `prod` |

### Render Deployment

#### Backend
- **Build Command:** `./gradlew jib`
- **Image:** `docker.io/tikam321/jira-ops-agent`
- **Port:** 8081
- **Environment Variables:** Set all required vars in Render dashboard

#### Frontend
- **Build Command:** Build image locally and push to Docker Hub, then use pre-built image
- **Image:** `docker.io/tikam321/jira-ops-frontend`
- **Port:** 80
- **Environment Variables:** None needed (URLs baked in at build time)

---

## Quick Reference

```bash
# ========================
# BACKEND
# ========================

# Local build
./gradlew clean build -x test

# Docker local
./gradlew jibDockerBuild

# Docker push
./gradlew jib

# ========================
# FRONTEND
# ========================

# Install
cd frontend && npm install

# Dev server
npm run dev

# Production build (local testing)
docker build -t tikam321/jira-ops-frontend:local \
  --build-arg VITE_API_BASE_URL=/api \
  --build-arg VITE_FRONTEND_URL=http://localhost \
  --build-arg BACKEND_URL=http://localhost:8081 .

# Production build (for Render)
docker build -t tikam321/jira-ops-frontend:latest \
  --build-arg VITE_API_BASE_URL=/api \
  --build-arg VITE_FRONTEND_URL=https://jira-ops-frontend.onrender.com \
  --build-arg BACKEND_URL=https://jira-ops-backend.onrender.com .

# Docker push
docker push tikam321/jira-ops-frontend:latest
```

---

## Environment Variables Summary

| Environment | `BACKEND_URL` | `VITE_FRONTEND_URL` |
|-------------|---------------|---------------------|
| Local (testing) | `http://localhost:8081` | `http://localhost` |
| Docker Compose | `http://backend:8081` | `http://localhost` |
| Production (Render) | `https://jira-ops-backend.onrender.com` | `https://jira-ops-frontend.onrender.com` |
