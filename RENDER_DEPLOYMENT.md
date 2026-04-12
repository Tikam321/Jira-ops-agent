# Render Deployment Guide

This guide provides step-by-step instructions to deploy Jira Ops Agent on Render.

---

## Prerequisites

1. **Docker Hub Account** with pushed images
2. **Render Account** (free tier available)
3. **Atlassian OAuth App** configured with production redirect URIs
4. **Groq API Key** from https://console.groq.com

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                        RENDER                                 │
│                                                             │
│   ┌─────────────────────┐  ┌─────────────────────┐      │
│   │     Frontend         │  │      Backend         │      │
│   │   (nginx:alpine)     │  │   (Spring Boot)     │      │
│   │   Port: 80           │  │   Port: 8081        │      │
│   │                      │  │                      │      │
│   │  Proxy /api/* ────────────► API Requests         │      │
│   │  Proxy /oauth2/* ──────────► OAuth Flow         │      │
│   └─────────────────────┘  └─────────────────────┘      │
│                                     │                      │
│                                     ▼                      │
│                           ┌─────────────────────┐          │
│                           │   PostgreSQL        │          │
│                           │   (Render DB)       │          │
│                           └─────────────────────┘          │
└─────────────────────────────────────────────────────────────┘
```

---

## Step 1: Push Docker Images to Docker Hub

### Backend Image (using Jib)

```bash
cd Jira-ops-agent

# Build and push to Docker Hub
./gradlew jib
```

### Frontend Image (manual build)

```bash
cd frontend

# Build with production URLs (replace with your actual Render URLs)
docker build -t tikam321/jira-ops-frontend:latest \
  --build-arg VITE_API_BASE_URL=/api \
  --build-arg VITE_FRONTEND_URL=https://jira-ops-frontend.onrender.com \
  --build-arg BACKEND_URL=https://jira-ops-backend.onrender.com .

# Push to Docker Hub
docker push tikam321/jira-ops-frontend:latest
```

**Verify images:**
```bash
docker images | grep jira-ops
```

---

## Step 2: Create Render Services

### 2.1 Create PostgreSQL Database

1. Go to: https://dashboard.render.com
2. Click **New +** → **PostgreSQL**
3. Configure:
   - **Name:** `jira-ops-db`
   - **Database:** `jira_ops`
   - **User:** (auto-generated or custom)
   - **Plan:** Free (or paid for production)
4. Click **Create Database**
5. Wait for status to be **Available**
6. Copy the **Internal Database URL** (you'll need this later)

### 2.2 Create Backend Service

1. Click **New +** → **Web Service**
2. Configure:
   - **Name:** `jira-ops-backend`
   - **Region:** (closest to you)
   - **Docker Hub Image:** `docker.io/tikam321/jira-ops-agent`
   - **Plan:** Free (or paid for production)
3. Click **Advanced**:
   - **Health Check Path:** `/actuator/health`
4. Click **Create Web Service**

### 2.3 Create Frontend Service

1. Click **New +** → **Web Service**
2. Configure:
   - **Name:** `jira-ops-frontend`
   - **Region:** (same as backend)
   - **Docker Hub Image:** `docker.io/tikam321/jira-ops-frontend`
   - **Plan:** Free (or paid for production)
3. Click **Advanced**:
   - **Health Check Path:** `/`
4. Click **Create Web Service**

---

## Step 3: Configure Backend Environment Variables

After backend service is created:

1. Go to **Backend Service** → **Environment** tab
2. Add these **Environment Variables**:

| Key | Value | Example |
|-----|-------|---------|
| `SPRING_PROFILES_ACTIVE` | `prod` | `prod` |
| `SPRING_DATASOURCE_URL` | PostgreSQL connection URL | `jdbc:postgresql://...` |
| `SPRING_DATASOURCE_USERNAME` | DB username | `your-db-user` |
| `SPRING_DATASOURCE_PASSWORD` | DB password | `your-db-password` |
| `JIRA_OAUTH_CLIENT_ID` | Jira OAuth Client ID | `your-jira-client-id` |
| `JIRA_OAUTH_CLIENT_SECRET` | Jira OAuth Client Secret | `your-jira-client-secret` |
| `GROQ_API_KEY` | Groq API Key | `gsk_...` |
| `FRONTEND_URL` | Frontend URL | `https://jira-ops-frontend.onrender.com` |

**To get SPRING_DATASOURCE_URL:**
- Go to PostgreSQL service → **Connections** tab
- Copy **Internal Database URL**
- Format: `jdbc:postgresql://host:port/dbname`

---

## Step 4: Configure Jira OAuth App

### 4.1 Update Redirect URIs

1. Go to: https://developer.atlassian.com/console
2. Select your app
3. Go to **Settings** → **OAuth 2.0**
4. Add these **Redirect URLs**:
   ```
   https://jira-ops-backend.onrender.com/login/oauth2/code/jira
   ```
5. Save changes

### 4.2 Get OAuth Credentials

1. Go to **Settings** → **Authentication**
2. Copy **Client ID** and **Client Secret**

---

## Step 5: Deploy and Test

### 5.1 Trigger Deployments

**Backend:**
1. Go to Backend service
2. Click **Manual Deploy** → **Deploy latest commit**

**Frontend:**
1. Go to Frontend service
2. Click **Manual Deploy** → **Deploy latest commit**

### 5.2 Verify Deployment

1. Check backend health: `https://jira-ops-backend.onrender.com/actuator/health`
2. Check frontend: `https://jira-ops-frontend.onrender.com`

### 5.3 Test Login Flow

1. Open frontend URL
2. Click **Login with Jira**
3. Complete Atlassian authentication
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

### CORS Errors
- Ensure `FRONTEND_URL` in backend matches exact frontend URL (including https)

### OAuth Redirect Errors
- Verify redirect URI in Atlassian Developer Console matches: `https://jira-ops-backend.onrender.com/login/oauth2/code/jira`

### Database Connection Errors
- Check PostgreSQL is in same region as backend
- Verify database credentials are correct
- Check connection string format

### 502 Bad Gateway
- Backend may not be running
- Check backend logs in Render dashboard

---

## Quick Reference

### Required Environment Variables (Backend)

```bash
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL=jdbc:postgresql://host:port/dbname
SPRING_DATASOURCE_USERNAME=db_user
SPRING_DATASOURCE_PASSWORD=db_password
JIRA_OAUTH_CLIENT_ID=your-client-id
JIRA_OAUTH_CLIENT_SECRET=your-client-secret
GROQ_API_KEY=gsk_...
FRONTEND_URL=https://jira-ops-frontend.onrender.com
```

### Docker Images

| Service | Image |
|---------|-------|
| Backend | `docker.io/tikam321/jira-ops-agent` |
| Frontend | `docker.io/tikam321/jira-ops-frontend` |
