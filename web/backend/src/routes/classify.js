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

// Configure multer for memory storage (max 10MB)
const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 10 * 1024 * 1024 },
  fileFilter: (req, file, cb) => {
    const allowed = ['image/jpeg', 'image/png', 'image/webp'];
    if (allowed.includes(file.mimetype)) {
      cb(null, true);
    } else {
      cb(new Error('Only JPEG, PNG, and WebP images are allowed'));
    }
  }
});

/**
 * POST /api/classify
 * Upload a food image and get classification results.
 * Body: multipart/form-data with 'image' field
 * Returns: { status, candidates: [{ food, nutrition, matchType, confidence, coachTip }] }
 */
router.post('/', upload.single('image'), async (req, res) => {
  try {
    if (!req.file) {
      return res.status(400).json({ error: 'No image file provided' });
    }

    const foodIndex = req.app.locals.foodIndex;
    if (!foodIndex) {
      return res.status(500).json({ error: 'Food index not ready' });
    }

    // 1. Classify the image
    const classification = await classifyFood(req.file.buffer);

    // 2. If we got ML results, match them against the food database
    let candidates = [];
    if (classification.results.length > 0) {
      const matches = matchClassifications(classification.results, foodIndex);
      candidates = matches
        .filter(m => m.matchedFood)
        .map(m => ({
          food: m.matchedFood,
          matchType: m.matchType,
          confidence: m.confidencePercent,
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

    res.json({
      status: classification.status,
      message: classification.message || null,
      candidateCount: candidates.length,
      candidates
    });

  } catch (error) {
    console.error('[Classify] Error:', error);
    res.status(500).json({ error: 'Classification failed', message: error.message });
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
