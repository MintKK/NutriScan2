---
description: How to run the NutriScan Cloud project locally for development
---

# Local Development Setup

// turbo-all

## Prerequisites
- Node.js 20+ installed
- Git
- (Optional) Python 3.10+ with `tflite-runtime` for local inference service

## Quick Start (2 terminals needed)

### Terminal 1: Backend API Server
```bash
cd web/backend
npm install --legacy-peer-deps
node src/server.js
```
Backend runs at **http://localhost:3001**.

The `.env` file should contain:
```
FIREBASE_SERVICE_ACCOUNT_PATH=./firebase-service-account.json
PORT=3001
NODE_ENV=development
FRONTEND_URL=http://localhost:5173
INFERENCE_SERVICE_URL=https://nutriscan-inference-245667741854.asia-southeast1.run.app
FOOD_DATA_PATH=../../app/src/main/assets/food_items.json
```

> **Tip:** The `INFERENCE_SERVICE_URL` points to the *live* Cloud Run inference service.
> You don't need to run the Python inference service locally — just reuse the deployed one.

### Terminal 2: Frontend Dev Server
```bash
cd web/frontend
npm install
npx vite --host
```
Frontend runs at **http://localhost:5173** with hot-reload.

## Testing
1. Open http://localhost:5173 in your browser
2. Sign up / log in
3. Navigate to **Add Meal** → **Photo** → upload or paste a food image URL
4. Check backend terminal for classification logs

## Health Check
Visit http://localhost:3001/api/health to verify:
- `foodIndexReady: true` — food database loaded
- `inferenceService.status: "ok"` — can reach ML inference service

## Architecture (Local Dev)
```
Browser (localhost:5173)  →  Backend (localhost:3001)  →  Inference (Cloud Run)
     Vite dev server           Express + Firebase           Python + TFLite
     Hot reload ✓              .env config                  Already deployed
```

## Notes
- Frontend changes are instant (Vite hot-reload)
- Backend changes require restarting `node src/server.js`  
  (or use `npm run dev` which uses `--watch` for auto-restart)
- Inference service changes require a Cloud Build push (or `gcloud run deploy`)
- The `firebase-service-account.json` in `web/backend/` is needed for Firestore access
