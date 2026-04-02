/**
 * Classification Route — POST /api/classify
 * Accepts image upload, runs food classifier + matching pipeline.
 */

const express = require('express');
const multer = require('multer');
const { classifyFood } = require('../services/foodClassifier');
const { matchClassifications } = require('../services/foodMatchingService');
const { getSuggestionForFood } = require('../services/aiCoach');
const { calculatePortionNutrition } = require('../services/nutritionCalculator');

const router = express.Router();

// Multer for image uploads (in-memory)
const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 10 * 1024 * 1024 },
  fileFilter: (req, file, cb) => {
    const allowed = ['image/jpeg', 'image/png', 'image/webp'];
    cb(null, allowed.includes(file.mimetype));
  }
});

/**
 * POST /api/classify/image
 * Accepts an image file upload, sends it to the ML inference service,
 * then runs the food matching pipeline on the results.
 * This is the CLOUD version — ML runs on the inference Cloud Run service,
 * not in the browser.
 */
router.post('/image', upload.single('image'), async (req, res) => {
  try {
    if (!req.file) {
      return res.status(400).json({ error: 'No image file provided' });
    }

    const foodIndex = req.app.locals.foodIndex;
    if (!foodIndex) {
      return res.status(500).json({ error: 'Food index not ready' });
    }

    // 1. Send image to the ML inference service (or local fallback)
    const { results, status: mlStatus, message: mlMessage } = await classifyFood(req.file.buffer);

    if (mlStatus === 'ERROR' || mlStatus === 'NO_MODEL') {
      return res.json({
        status: mlStatus,
        message: mlMessage || 'ML inference unavailable. Please use manual food search.',
        candidates: []
      });
    }

    if (results.length === 0) {
      return res.json({
        status: 'NO_FOOD_DETECTED',
        message: 'No food detected in the image. Try a clearer photo or search manually.',
        candidates: []
      });
    }

    // 2. Match ML labels to food database entries
    const matches = matchClassifications(results, foodIndex);
    const candidates = matches
      .filter(m => m.matchedFood)
      .map(m => ({
        food: m.matchedFood,
        matchType: m.matchType,
        confidence: Math.round(m.confidencePercent),
        combinedScore: Math.round(m.combinedScore * 100),
        mlLabel: m.mlLabel,
        portions: {
          '100g': calculatePortionNutrition(m.matchedFood, 100),
          '150g': calculatePortionNutrition(m.matchedFood, 150),
          '250g (Bowl)': calculatePortionNutrition(m.matchedFood, 250),
          '500g (Plate)': calculatePortionNutrition(m.matchedFood, 500)
        },
        coachTip: getSuggestionForFood(m.matchedFood)
      }));

    const status = candidates.length > 0
      ? (candidates[0].confidence >= 70 ? 'HIGH_CONFIDENCE' : 'MULTIPLE_CANDIDATES')
      : 'NO_FOOD_DETECTED';

    res.json({
      status,
      candidateCount: candidates.length,
      candidates
    });

  } catch (error) {
    console.error('[Classify/Image] Error:', error);
    res.status(500).json({ error: 'Image classification failed', message: error.message });
  }
});

/**
 * POST /api/classify
 * Accepts an array of ML results from the frontend TF.js wrapper
 * and runs them through the food matching pipeline.
 * Body: { results: [{ label, confidence }] }
 * Returns: { status, candidates: [{ food, nutrition, matchType, confidence, coachTip }] }
 */
router.post('/', (req, res) => {
  try {
    const { results } = req.body;
    
    if (!results || !Array.isArray(results)) {
      console.error('[Classify] Invalid body received:', JSON.stringify(req.body));
      return res.status(400).json({ error: 'No ML results provided' });
    }

    const foodIndex = req.app.locals.foodIndex;
    if (!foodIndex) {
      return res.status(500).json({ error: 'Food index not ready' });
    }

    // 1. Run inference results through the food matching pipeline
    let candidates = [];
    if (results.length > 0) {
      const matches = matchClassifications(results, foodIndex);
      candidates = matches
        .filter(m => m.matchedFood)
        .map(m => ({
          food: m.matchedFood,
          matchType: m.matchType,
          confidence: Math.round(m.confidencePercent),
          combinedScore: Math.round(m.combinedScore * 100),
          mlLabel: m.mlLabel,
          // Pre-calculate nutrition for common portions
          portions: {
            '100g': calculatePortionNutrition(m.matchedFood, 100),
            '150g': calculatePortionNutrition(m.matchedFood, 150),
            '250g (Bowl)': calculatePortionNutrition(m.matchedFood, 250),
            '500g (Plate)': calculatePortionNutrition(m.matchedFood, 500)
          },
          // AI Coach suggestion
          coachTip: getSuggestionForFood(m.matchedFood)
        }));
    }

    const status = candidates.length > 0
      ? (candidates[0].confidence >= 70 ? 'HIGH_CONFIDENCE' : (candidates.length === 1 ? 'SINGLE_MATCH' : 'MULTIPLE_CANDIDATES'))
      : 'NO_FOOD_DETECTED';

    res.json({
      status,
      message: candidates.length === 0 ? 'No food matches found in database.' : null,
      candidateCount: candidates.length,
      candidates
    });

  } catch (error) {
    console.error('[Classify] Error:', error);
    res.status(500).json({ error: 'Classification matching failed', message: error.message });
  }
});

/**
 * POST /api/classify/portion
 * Calculate nutrition for a specific food and portion size.
 * Body: { foodName: string, grams: number }
 */
router.post('/portion', (req, res) => {
  try {
    const { foodName, grams } = req.body;
    const foodIndex = req.app.locals.foodIndex;

    if (!foodName || !grams) {
      return res.status(400).json({ error: 'foodName and grams are required' });
    }

    const food = foodIndex.findByExactName(foodName.toLowerCase()) ||
                 foodIndex.findByAlias(foodName.toLowerCase());

    if (!food) {
      return res.status(404).json({ error: 'Food not found' });
    }

    const nutrition = calculatePortionNutrition(food, grams);
    const coachTip = getSuggestionForFood(food);

    res.json({ food, nutrition, grams, coachTip });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

/**
 * GET /api/classify/search?q=query
 * Search foods by name (for manual food entry).
 */
router.get('/search', (req, res) => {
  try {
    const query = (req.query.q || '').toLowerCase().trim();
    if (query.length < 2) {
      return res.status(400).json({ error: 'Query must be at least 2 characters' });
    }

    const foodIndex = req.app.locals.foodIndex;
    const results = foodIndex.allFoods
      .filter(f => f.name.toLowerCase().includes(query) ||
                   (f.aliases && f.aliases.toLowerCase().includes(query)))
      .slice(0, 20)
      .map(f => ({
        food: f,
        portions: {
          '100g': calculatePortionNutrition(f, 100),
          '250g (Bowl)': calculatePortionNutrition(f, 250)
        }
      }));

    res.json({ query, count: results.length, results });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

module.exports = router;
