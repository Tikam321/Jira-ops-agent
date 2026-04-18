# Render Deployment Guide

This guide explains how to deploy Jira Ops Agent on Render using pre-built Docker images.

---

## Pre-built Images

| Service | Image |
|---------|-------|
| Backend | `docker.io/tikam321/jira-ops-agent:amd64` |
| Frontend | `docker.io/tikam321/jira-ops-frontend:amd64` |

---

## Build and Push Commands (Mac Apple Silicon)

### Prerequisites

Ensure Docker buildx is set up for multi-platform builds:

```bash
# Create builder
docker buildx create --name jira-builder

# Use the builder
docker buildx use jira-builder

# Verify platforms
docker buildx inspect --bootstrap | grep Platforms
```

### Backend Image

```bash
cd Jira-ops-agent

# Build for linux/amd64 and push to Docker Hub
docker buildx build --platform linux/amd64 \
  --push \
  -t docker.io/tikam321/jira-ops-agent:amd64 .
```

### Frontend Image

```bash
cd frontend

# Build for linux/amd64 and push to Docker Hub
# Replace BACKEND_URL with your actual backend URL after deployment
docker buildx build --platform linux/amd64 \
  --push \
  -t docker.io/tikam321/jira-ops-frontend:amd64 \
  --build-arg VITE_API_BASE_URL=/api \
  --build-arg VITE_FRONTEND_URL=https://jira-ops-frontend.onrender.com \
  --build-arg BACKEND_URL=https://jira-ops-backend.onrender.com .
```

---

## Quick Build Commands (Copy-Paste)

### Backend
```bash
cd Jira-ops-agent
docker buildx build --platform linux/amd64 --push -t docker.io/tikam321/jira-ops-agent:amd64 .
```

### Frontend (with correct URLs)
```bash
cd frontend
docker buildx build --platform linux/amd64 --push -t docker.io/tikam321/jira-ops-frontend:amd64 \
  --build-arg VITE_API_BASE_URL=/api \
  --build-arg VITE_FRONTEND_URL=https://jira-ops-frontend.onrender.com \
  --build-arg BACKEND_URL=https://jira-ops-backend.onrender.com .
```

---

## Deployment Steps

### Step 1: Create PostgreSQL Database

1. Go to: https://dashboard.render.com
2. Click **New +** → **PostgreSQL**
3. Configure:
   - **Name:** `jira-ops-db`
   - **Database:** `jira_ops`
   - **Plan:** Free
4. Click **Create Database**
5. Wait for status: **Available**
6. Go to **Connections** tab → Copy **Internal Database URL**

### Step 2: Deploy Backend

1. **New +** → **Web Service**
2. Select **Use an existing Docker image**
3. Enter: `docker.io/tikam321/jira-ops-agent:amd64`
4. Configure:
   - **Name:** `jira-ops-backend`
   - **Region:** Singapore
5. Click **Advanced** → **Add Environment Variables**:

| Key | Value |
|-----|-------|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `FRONTEND_URL` | `https://jira-ops-frontend.onrender.com` |
| `SPRING_DATASOURCE_URL` | (paste Internal Database URL from Step 1) |
| `SPRING_DATASOURCE_USERNAME` | (from PostgreSQL) |
| `SPRING_DATASOURCE_PASSWORD` | (from PostgreSQL) |
| `JIRA_OAUTH_CLIENT_ID` | (from Atlassian Developer Console) |
| `JIRA_OAUTH_CLIENT_SECRET` | (from Atlassian Developer Console) |
| `GROQ_API_KEY` | (from Groq Console) |

6. Click **Create Web Service**

### Step 3: Get Backend URL

After backend deploys, copy the URL from Render dashboard (e.g., `https://jira-ops-backend.onrender.com`)

### Step 4: Build Frontend with Correct Backend URL

```bash
cd frontend
docker buildx build --platform linux/amd64 --push -t docker.io/tikam321/jira-ops-frontend:amd64 \
  --build-arg VITE_API_BASE_URL=/api \
  --build-arg VITE_FRONTEND_URL=https://jira-ops-frontend.onrender.com \
  --build-arg BACKEND_URL=https://jira-ops-backend.onrender.com .
```

### Step 5: Deploy Frontend

1. **New +** → **Web Service**
2. Select **Use an existing Docker image**
3. Enter: `docker.io/tikam321/jira-ops-frontend:amd64`
4. Configure:
   - **Name:** `jira-ops-frontend`
   - **Region:** Singapore
5. Click **Create Web Service**

### Step 6: Update Backend FRONTEND_URL

After frontend deploys, go to Backend service → Environment → Update `FRONTEND_URL` with actual frontend URL

### Step 7: Configure Jira OAuth

1. Go to: https://developer.atlassian.com/console
2. Select your app
3. Go to **Settings** → **OAuth 2.0**
4. Add **Redirect URLs**:

```
http://localhost/login/oauth2/code/jira                    # Local development
https://jira-ops-backend.onrender.com/login/oauth2/code/jira  # Production
```

### Step 8: Test

1. Open frontend URL
2. Click **Login with Jira**
3. Complete authentication
4. Verify redirect to dashboard

---

## Important URLs

| Service | URL |
|---------|-----|
| Frontend | `https://jira-ops-frontend.onrender.com` |
| Backend | `https://jira-ops-backend.onrender.com` |
| Backend Health | `https://jira-ops-backend.onrender.com/actuator/health` |
| Render Dashboard | https://dashboard.render.com |

---

## Troubleshooting

### 502 Bad Gateway
- Backend may not be running
- Check backend logs in Render dashboard
- Verify backend health: `curl https://jira-ops-backend.onrender.com/actuator/health`

### OAuth Redirect Error
- Verify redirect URI in Atlassian matches: `https://jira-ops-backend.onrender.com/login/oauth2/code/jira`

### CORS Error
- Ensure `FRONTEND_URL` in backend matches exact frontend URL

### "Invalid Platform" Error
- Image must be built for `linux/amd64` (Render requirement)
- Use `docker buildx build --platform linux/amd64`

---

## render.yaml (Optional - for GitHub Integration)

```yaml
services:
  - name: jira-ops-backend
    image:
      url: docker.io/tikam321/jira-ops-agent:amd64

  - name: jira-ops-frontend
    image:
      url: docker.io/tikam321/jira-ops-frontend:amd64
```


## for frontend redner deplyment script



cd frontend(previous use)
docker build --platform linux/amd64 -t tikam321/jira-ops-frontend:latest \
--build-arg VITE_API_BASE_URL=/api/v1 \
--build-arg VITE_FRONTEND_URL=https://jira-ops-frontend.onrender.com \
--build-arg BACKEND_URL=https://jira-ops-backend.onrender.com \
--build-arg BACKEND_HOST=jira-ops-backend.onrender.com .

docker push tikam321/jira-ops-frontend:latest

(current deployment command)
cd frontend
docker build --platform linux/amd64 -t tikam321/jira-ops-frontend:latest .
docker push tikam321/jira-ops-frontend:latest

## for backend redner deplyment script
docker buildx build --platform linux/amd64 --push -t docker.io/tikam321/jira-ops-agent:amd64 .
./gradlew jib --image=docker.io/tikam321/jira-ops-agent:latest 