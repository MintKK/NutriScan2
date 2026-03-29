/**
 * NutriScan Cloud — Backend API Server
 * Main entry point. Initializes Firebase, loads ML assets, mounts routes.
 */

const express = require('express');
const cors = require('cors');
const path = require('path');
const fs = require('fs');
const rateLimit = require('express-rate-limit');

// Load environment variables
require('dotenv').config();

const admin = require('firebase-admin');
const FoodAliasIndex = require('./services/foodAliasIndex');
const { initialize: initClassifier } = require('./services/foodClassifier');

const app = express();
const PORT = process.env.PORT || 3001;

// ============ MIDDLEWARE ============

// CORS: allow multiple origins (comma-separated FRONTEND_URL or defaults)
const allowedOrigins = (process.env.FRONTEND_URL || 'http://localhost:5173')
  .split(',')
  .map(o => o.trim());

app.use(cors({
  origin: function (origin, callback) {
    // Allow requests with no origin (e.g., server-to-server, health checks)
    if (!origin) return callback(null, true);
    if (allowedOrigins.includes(origin) || allowedOrigins.includes('*')) {
      return callback(null, true);
    }
    callback(new Error('Not allowed by CORS'));
  },
  credentials: true
}));
app.use(express.json({ limit: '1mb' }));

// Rate limiting
const apiLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 200,
  message: { error: 'Too many requests, please try again later' }
});
app.use('/api/', apiLimiter);

// Stricter rate limit for auth endpoints
const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 20,
  message: { error: 'Too many authentication attempts' }
});

// ============ FIREBASE INIT ============

function initFirebase() {
  const PROJECT_ID = process.env.FIREBASE_PROJECT_ID || 'nutriscan-cloud';
  
  try {
    const serviceAccountPath = process.env.FIREBASE_SERVICE_ACCOUNT_PATH || './firebase-service-account.json';
    
    if (fs.existsSync(serviceAccountPath)) {
      const serviceAccount = JSON.parse(fs.readFileSync(serviceAccountPath, 'utf-8'));
      admin.initializeApp({
        credential: admin.credential.cert(serviceAccount),
        storageBucket: process.env.FIREBASE_STORAGE_BUCKET || 'nutriscan-cloud.firebasestorage.app'
      });
      console.log('[Server] Firebase initialized with service account');
    } else {
      // Initialize with just project ID — token verification still works
      // (Firebase Admin uses Google's public keys to verify tokens)
      admin.initializeApp({ 
        projectId: PROJECT_ID,
        storageBucket: process.env.FIREBASE_STORAGE_BUCKET || 'nutriscan-cloud.firebasestorage.app'
      });
      console.log(`[Server] Firebase initialized with project ID: ${PROJECT_ID}`);
      console.log('[Server] Note: Firestore writes require a service account. Token verification works.');
    }
  } catch (error) {
    console.error('[Server] Firebase init error:', error.message);
  }
}

// ============ FOOD DATA INIT ============

async function initFoodData() {
  // Check container-local path first, then fallback to Android assets path (dev)
  const containerLocalPath = path.resolve(__dirname, '..', 'data', 'food_items.json');
  const androidAssetsPath = path.resolve(__dirname, '../../..', 'app/src/main/assets/food_items.json');
  const foodDataPath = process.env.FOOD_DATA_PATH || 
    (fs.existsSync(containerLocalPath) ? containerLocalPath : androidAssetsPath);

  console.log(`[Server] Loading food data from: ${foodDataPath}`);

  if (!fs.existsSync(foodDataPath)) {
    console.error(`[Server] food_items.json not found at: ${foodDataPath}`);
    return null;
  }

  const rawData = fs.readFileSync(foodDataPath, 'utf-8');
  const foods = JSON.parse(rawData);
  console.log(`[Server] Loaded ${foods.length} food items`);

  const index = new FoodAliasIndex(foods);
  console.log(`[Server] Food alias index built: ${index.size()} names indexed`);

  return index;
}

// ============ ML CLASSIFIER INIT ============

async function initML() {
  const modelPath = process.env.MODEL_PATH ||
    path.resolve(__dirname, '../../..', 'app/src/main/assets/ml/food11.tflite');
  const labelsPath = process.env.LABELS_PATH ||
    path.resolve(__dirname, '../../..', 'app/src/main/assets/ml/food11_labels.txt');

  console.log(`[Server] Initializing ML classifier...`);
  await initClassifier(modelPath, labelsPath);
}

// ============ ROUTES ============

const classifyRoutes = require('./routes/classify');
const mealsRoutes = require('./routes/meals');
const socialRoutes = require('./routes/social');
const authRoutes = require('./routes/auth');
const uploadRoutes = require('./routes/upload');
const proxyImageRoutes = require('./routes/proxyImage');

// Serve uploaded images as static files
app.use('/uploads', express.static(path.resolve(__dirname, '../uploads')));

app.use('/api/classify', classifyRoutes);
app.use('/api/meals', mealsRoutes);
app.use('/api/social', socialRoutes);
app.use('/api/auth', authLimiter, authRoutes);
app.use('/api/upload', uploadRoutes);
app.use('/api/proxy-image', proxyImageRoutes);

// Health check
app.get('/api/health', (req, res) => {
  res.json({
    status: 'ok',
    service: 'NutriScan Cloud API',
    foodIndexReady: !!app.locals.foodIndex,
    timestamp: new Date().toISOString()
  });
});

// ============ START ============

async function start() {
  console.log('\n========================================');
  console.log('  NutriScan Cloud — API Server');
  console.log('========================================\n');

  // 1. Initialize Firebase
  initFirebase();

  // 2. Load food database and build alias index
  const foodIndex = await initFoodData();
  if (foodIndex) {
    app.locals.foodIndex = foodIndex;
  }

  // 3. Initialize ML classifier (best-effort)
  try {
    await initML();
  } catch (e) {
    console.warn(`[Server] ML classifier init warning: ${e.message}`);
    console.warn('[Server] Manual food search will still work.');
  }

  // 4. Start listening
  app.listen(PORT, () => {
    console.log(`\n[Server] API running at http://localhost:${PORT}`);
    console.log(`[Server] Health check: http://localhost:${PORT}/api/health`);
    console.log(`[Server] Food search: http://localhost:${PORT}/api/classify/search?q=apple\n`);
  });
}

start().catch(err => {
  console.error('[Server] Fatal error:', err);
  process.exit(1);
});
