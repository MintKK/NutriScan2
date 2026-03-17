/**
 * Auth/Profile Routes — User profile management.
 */

const express = require('express');
const admin = require('firebase-admin');
const { requireAuth } = require('../middleware/auth');
const { calculateTargets } = require('../services/nutritionCalculator');

const router = express.Router();
const db = () => admin.firestore();

/**
 * POST /api/auth/profile — Create or update user profile
 * Body: { username, displayname, bio, profileImageUrl }
 */
router.post('/profile', requireAuth, async (req, res) => {
  try {
    const { username, displayname, bio, profileImageUrl } = req.body;

    const existingDoc = await db().collection('users').doc(req.user.uid).get();
    const isNew = !existingDoc.exists;

    const profileData = {
      uid: req.user.uid,
      email: req.user.email,
      username: username || req.user.email.split('@')[0],
      displayname: displayname || username || '',
      bio: bio || '',
      profileImageUrl: profileImageUrl || '',
      ...(isNew ? {
        numFollowers: 0,
        numFollowing: 0,
        numPosts: 0,
        created: Date.now()
      } : {}),
      updated: Date.now()
    };

    await db().collection('users').doc(req.user.uid).set(profileData, { merge: true });

    res.status(isNew ? 201 : 200).json(profileData);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

/**
 * GET /api/auth/profile — Get current user's profile
 */
router.get('/profile', requireAuth, async (req, res) => {
  try {
    const doc = await db().collection('users').doc(req.user.uid).get();

    if (!doc.exists) {
      return res.status(404).json({ error: 'Profile not found. Please complete registration.' });
    }

    res.json({ user: doc.data() });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

/**
 * POST /api/auth/questionnaire — Save questionnaire results and computed targets
 * Body: { gender, age, heightCm, weightKg, activityLevel, goal }
 */
router.post('/questionnaire', requireAuth, async (req, res) => {
  try {
    const { gender, age, heightCm, weightKg, activityLevel, goal } = req.body;

    if (!gender || !age || !heightCm || !weightKg || !activityLevel || !goal) {
      return res.status(400).json({ error: 'All questionnaire fields are required' });
    }

    // Calculate nutrition targets using the Mifflin-St Jeor engine
    const targets = calculateTargets({
      weightKg: parseFloat(weightKg),
      heightCm: parseFloat(heightCm),
      age: parseInt(age),
      gender,
      activityLevel,
      goal
    });

    // Save to user profile
    await db().collection('users').doc(req.user.uid).set({
      gender,
      age: parseInt(age),
      heightCm: parseFloat(heightCm),
      weightKg: parseFloat(weightKg),
      activityLevel,
      goal,
      calorieGoal: targets.calories,
      proteinGoalG: targets.proteinGrams,
      carbGoalG: targets.carbGrams,
      fatGoalG: targets.fatGrams,
      questionnaireCompleted: true,
      updated: Date.now()
    }, { merge: true });

    res.json({ targets, message: 'Questionnaire saved and targets calculated' });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

module.exports = router;
