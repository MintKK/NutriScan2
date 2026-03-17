/**
 * Meals Routes — CRUD for meal logging via Firestore.
 */

const express = require('express');
const admin = require('firebase-admin');
const { requireAuth } = require('../middleware/auth');
const { calculatePortionNutrition } = require('../services/nutritionCalculator');
const { generateInsights } = require('../services/aiCoach');

const router = express.Router();
const db = () => admin.firestore();

// ============ MEAL LOGGING ============

/**
 * POST /api/meals — Log a new meal
 * Body: { foodName, kcalPer100g, proteinPer100g, carbsPer100g, fatPer100g, grams, imageUrl? }
 */
router.post('/', requireAuth, async (req, res) => {
  try {
    const { foodName, kcalPer100g, proteinPer100g, carbsPer100g, fatPer100g, grams, imageUrl } = req.body;

    if (!foodName || !grams) {
      return res.status(400).json({ error: 'foodName and grams are required' });
    }

    const food = { kcalPer100g, proteinPer100g, carbsPer100g, fatPer100g };
    const nutrition = calculatePortionNutrition(food, grams);

    const mealLog = {
      userId: req.user.uid,
      foodName,
      grams,
      kcalTotal: nutrition.kcal,
      proteinTotal: nutrition.protein,
      carbsTotal: nutrition.carbs,
      fatTotal: nutrition.fat,
      imageUrl: imageUrl || null,
      timestamp: admin.firestore.FieldValue.serverTimestamp(),
      date: new Date().toISOString().split('T')[0] // YYYY-MM-DD
    };

    const docRef = await db().collection('meal_logs').add(mealLog);

    res.status(201).json({ id: docRef.id, ...mealLog, timestamp: new Date().toISOString() });
  } catch (error) {
    console.error('[Meals] Create error:', error);
    res.status(500).json({ error: error.message });
  }
});

/**
 * GET /api/meals/today — Get today's meals for the authenticated user
 */
router.get('/today', requireAuth, async (req, res) => {
  try {
    const today = new Date().toISOString().split('T')[0];

    const snapshot = await db().collection('meal_logs')
      .where('userId', '==', req.user.uid)
      .where('date', '==', today)
      .get();

    const meals = snapshot.docs
      .map(doc => ({ id: doc.id, ...doc.data() }))
      .sort((a, b) => (b.timestamp?._seconds || 0) - (a.timestamp?._seconds || 0));

    // Calculate daily totals
    const totals = meals.reduce((acc, m) => ({
      kcal: acc.kcal + (m.kcalTotal || 0),
      protein: acc.protein + (m.proteinTotal || 0),
      carbs: acc.carbs + (m.carbsTotal || 0),
      fat: acc.fat + (m.fatTotal || 0)
    }), { kcal: 0, protein: 0, carbs: 0, fat: 0 });

    res.json({ date: today, meals, totals, count: meals.length });
  } catch (error) {
    console.error('[Meals] Today error:', error);
    res.status(500).json({ error: error.message });
  }
});

/**
 * GET /api/meals/weekly — Get last 7 days' daily calorie totals
 */
router.get('/weekly', requireAuth, async (req, res) => {
  try {
    const days = [];
    for (let i = 6; i >= 0; i--) {
      const d = new Date();
      d.setDate(d.getDate() - i);
      days.push(d.toISOString().split('T')[0]);
    }

    const snapshot = await db().collection('meal_logs')
      .where('userId', '==', req.user.uid)
      .where('date', 'in', days)
      .get();

    const dailyTotals = {};
    for (const day of days) {
      dailyTotals[day] = { kcal: 0, protein: 0, carbs: 0, fat: 0, mealCount: 0 };
    }

    snapshot.docs.forEach(doc => {
      const data = doc.data();
      if (dailyTotals[data.date]) {
        dailyTotals[data.date].kcal += data.kcalTotal || 0;
        dailyTotals[data.date].protein += data.proteinTotal || 0;
        dailyTotals[data.date].carbs += data.carbsTotal || 0;
        dailyTotals[data.date].fat += data.fatTotal || 0;
        dailyTotals[data.date].mealCount++;
      }
    });

    const weeklyData = days.map(day => ({
      date: day,
      dayLabel: new Date(day + 'T00:00:00').toLocaleDateString('en-US', { weekday: 'short' }),
      ...dailyTotals[day]
    }));

    res.json({ weeklyData });
  } catch (error) {
    console.error('[Meals] Weekly error:', error);
    res.status(500).json({ error: error.message });
  }
});

/**
 * GET /api/meals/diary/:date — Get all meals for a specific date
 */
router.get('/diary/:date', requireAuth, async (req, res) => {
  try {
    const { date } = req.params;

    const snapshot = await db().collection('meal_logs')
      .where('userId', '==', req.user.uid)
      .where('date', '==', date)
      .get();

    const meals = snapshot.docs
      .map(doc => ({ id: doc.id, ...doc.data() }))
      .sort((a, b) => (b.timestamp?._seconds || 0) - (a.timestamp?._seconds || 0));

    const totals = meals.reduce((acc, m) => ({
      kcal: acc.kcal + (m.kcalTotal || 0),
      protein: acc.protein + (m.proteinTotal || 0),
      carbs: acc.carbs + (m.carbsTotal || 0),
      fat: acc.fat + (m.fatTotal || 0)
    }), { kcal: 0, protein: 0, carbs: 0, fat: 0 });

    res.json({ date, meals, totals, count: meals.length });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

/**
 * DELETE /api/meals/:id — Delete a meal log
 */
router.delete('/:id', requireAuth, async (req, res) => {
  try {
    const docRef = db().collection('meal_logs').doc(req.params.id);
    const doc = await docRef.get();

    if (!doc.exists) {
      return res.status(404).json({ error: 'Meal not found' });
    }

    if (doc.data().userId !== req.user.uid) {
      return res.status(403).json({ error: 'Not authorized' });
    }

    await docRef.delete();
    res.json({ success: true });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

/**
 * GET /api/meals/insights — Get AI Coach insights for current daily state
 */
router.get('/insights', requireAuth, async (req, res) => {
  try {
    const today = new Date().toISOString().split('T')[0];

    const snapshot = await db().collection('meal_logs')
      .where('userId', '==', req.user.uid)
      .where('date', '==', today)
      .get();

    const meals = snapshot.docs.map(doc => doc.data());
    const macros = meals.reduce((acc, m) => ({
      protein: acc.protein + (m.proteinTotal || 0),
      carbs: acc.carbs + (m.carbsTotal || 0),
      fat: acc.fat + (m.fatTotal || 0)
    }), { protein: 0, carbs: 0, fat: 0 });

    const currentCalories = meals.reduce((sum, m) => sum + (m.kcalTotal || 0), 0);

    // Get user targets from profile
    let calorieGoal = 2000, proteinGoalG = 0, carbGoalG = 0, fatGoalG = 0;
    try {
      const profileDoc = await db().collection('users').doc(req.user.uid).get();
      if (profileDoc.exists) {
        const profile = profileDoc.data();
        calorieGoal = profile.calorieGoal || 2000;
        proteinGoalG = profile.proteinGoalG || 0;
        carbGoalG = profile.carbGoalG || 0;
        fatGoalG = profile.fatGoalG || 0;
      }
    } catch (e) { /* use defaults */ }

    const insights = generateInsights({
      currentCalories,
      calorieGoal,
      macros,
      proteinGoalG,
      carbGoalG,
      fatGoalG
    });

    res.json({ insights, currentCalories, macros, calorieGoal });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

module.exports = router;
