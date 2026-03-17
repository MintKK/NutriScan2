/**
 * AI Coach — Direct port of AICoachRepository.kt
 * Generates contextual dietary insights and smart food swap suggestions.
 */

// ============ DATA MODELS ============

const InsightType = {
  SUCCESS: 'SUCCESS',
  INFO: 'INFO',
  WARNING: 'WARNING',
  TIP: 'TIP'
};

// ============ SMART SWAP MAP ============
// Maps common high-calorie foods to healthier alternatives (from AICoachRepository.kt)
const SWAP_MAP = {
  'pizza': { alternative: 'Grilled Chicken Wrap', reason: '~250 fewer kcal, +15g protein' },
  'donut': { alternative: 'Greek Yogurt with Honey', reason: '~200 fewer kcal, +12g protein' },
  'doughnut': { alternative: 'Greek Yogurt with Honey', reason: '~200 fewer kcal, +12g protein' },
  'burger': { alternative: 'Turkey Lettuce Wrap', reason: '~300 fewer kcal, less saturated fat' },
  'fries': { alternative: 'Sweet Potato Wedges', reason: '~100 fewer kcal, more fiber & vitamin A' },
  'french fries': { alternative: 'Sweet Potato Wedges', reason: '~100 fewer kcal, more fiber & vitamin A' },
  'fried chicken': { alternative: 'Grilled Chicken Breast', reason: '~150 fewer kcal, -10g fat' },
  'fried rice': { alternative: 'Steamed Rice with Veggies', reason: '~120 fewer kcal, less oil' },
  'ice cream': { alternative: 'Frozen Yogurt', reason: '~100 fewer kcal, +5g protein' },
  'chocolate': { alternative: 'Dark Chocolate (85%)', reason: 'Less sugar, more antioxidants' },
  'cake': { alternative: 'Protein Bar', reason: '~150 fewer kcal, +15g protein' },
  'pasta': { alternative: 'Zucchini Noodles with Sauce', reason: '~200 fewer kcal, more fiber' },
  'soda': { alternative: 'Sparkling Water with Lemon', reason: '~140 fewer kcal, zero sugar' },
  'chips': { alternative: 'Air-popped Popcorn', reason: '~100 fewer kcal, whole grain fiber' },
  'nutella': { alternative: 'Almond Butter', reason: 'Less sugar, healthy fats, +3g protein' },
  'hot dog': { alternative: 'Chicken Sausage', reason: '~80 fewer kcal, less sodium' },
  'pancake': { alternative: 'Oat Pancakes', reason: 'More fiber, +5g protein, slower energy release' },
  'waffle': { alternative: 'Oat Pancakes', reason: 'More fiber, +5g protein, slower energy release' },
  'bread': { alternative: 'Whole Grain Bread', reason: 'More fiber, slower blood sugar rise' },
  'candy': { alternative: 'Mixed Nuts (30g)', reason: 'Healthy fats, protein, and sustained energy' },
  'cookie': { alternative: 'Apple Slices with Peanut Butter', reason: '~100 fewer kcal, more fiber & protein' }
};

// ============ INSIGHT GENERATORS ============

function generateMorningInsight(currentCalories, calorieGoal) {
  if (currentCalories === 0) {
    return {
      emoji: '🌅',
      message: `Good morning! Start your day right — a balanced breakfast sets the tone for hitting your ${calorieGoal} kcal goal.`,
      type: InsightType.TIP
    };
  }
  return null;
}

function generateAfternoonInsight(currentCalories, calorieGoal) {
  if (calorieGoal <= 0) return null;
  const pct = Math.round((currentCalories * 100) / calorieGoal);

  if (pct < 30) {
    return {
      emoji: '🍽️',
      message: `You're only at ${pct}% of your daily goal. Don't skip lunch — your body needs fuel!`,
      type: InsightType.WARNING
    };
  }
  if (pct >= 40 && pct <= 60) {
    return {
      emoji: '👍',
      message: `Nice pacing! You're at ${pct}% by afternoon — right on track.`,
      type: InsightType.SUCCESS
    };
  }
  return null;
}

function generateEveningInsight(currentCalories, calorieGoal) {
  if (calorieGoal <= 0) return null;
  const pct = Math.round((currentCalories * 100) / calorieGoal);

  if (pct > 110) {
    return {
      emoji: '⚠️',
      message: `You're ${pct - 100}% over your calorie goal. Consider a lighter dinner or a walk!`,
      type: InsightType.WARNING
    };
  }
  if (pct >= 85 && pct <= 105) {
    return {
      emoji: '🎯',
      message: `Almost perfect! You're at ${pct}% of your daily goal — great discipline today!`,
      type: InsightType.SUCCESS
    };
  }
  if (pct < 60) {
    return {
      emoji: '🍽️',
      message: `Only ${pct}% of your goal with the day nearly done. Make sure you're eating enough!`,
      type: InsightType.WARNING
    };
  }
  return null;
}

function generateMacroInsight(macros, calorieGoal, userProteinGoalG = 0, userCarbGoalG = 0, userFatGoalG = 0) {
  if (calorieGoal <= 0) return null;

  const proteinGoalG = userProteinGoalG > 0 ? userProteinGoalG : calorieGoal * 0.25 / 4;
  const carbGoalG = userCarbGoalG > 0 ? userCarbGoalG : calorieGoal * 0.50 / 4;
  const fatGoalG = userFatGoalG > 0 ? userFatGoalG : calorieGoal * 0.25 / 9;

  const proteinPct = proteinGoalG > 0 ? Math.round((macros.protein / proteinGoalG) * 100) : 0;
  const carbPct = carbGoalG > 0 ? Math.round((macros.carbs / carbGoalG) * 100) : 0;
  const fatPct = fatGoalG > 0 ? Math.round((macros.fat / fatGoalG) * 100) : 0;

  if (proteinPct < 40 && macros.protein > 0) {
    return {
      emoji: '🥩',
      message: `Protein is at ${proteinPct}% of your target. Try adding Greek yogurt, eggs, or chicken to your next meal!`,
      type: InsightType.TIP
    };
  }
  if (carbPct < 40 && macros.carbs > 0) {
    return {
      emoji: '🍚',
      message: `Carbs are at ${carbPct}% — consider some oatmeal, rice, or fruit to fuel your energy.`,
      type: InsightType.TIP
    };
  }
  if (fatPct > 120) {
    return {
      emoji: '🫒',
      message: `Fat intake is at ${fatPct}% of your goal. Watch out for fried or oily foods in your remaining meals.`,
      type: InsightType.WARNING
    };
  }
  if (proteinPct >= 90 && proteinPct <= 110 && carbPct >= 80 && carbPct <= 120) {
    return {
      emoji: '⚖️',
      message: "Your macros are beautifully balanced today! Keep it up 💪",
      type: InsightType.SUCCESS
    };
  }
  return null;
}

function generateWaterInsight(currentWaterMl, waterGoalMl) {
  if (waterGoalMl <= 0) return null;
  const waterPct = Math.round((currentWaterMl * 100) / waterGoalMl);
  const hour = new Date().getHours();

  if (waterPct >= 100) {
    return {
      emoji: '🎉',
      message: "You've hit your water goal for today! Stay consistent 💧",
      type: InsightType.SUCCESS
    };
  }
  if (waterPct < 30 && hour > 14) {
    return {
      emoji: '💧',
      message: `Only ${waterPct}% of your water goal and it's past 2PM. Try keeping a bottle on your desk!`,
      type: InsightType.WARNING
    };
  }
  return null;
}

// ============ MAIN API ============

/**
 * Generate contextual insights based on user's current daily state.
 * @returns {Array} Top 3 insights
 */
function generateInsights({
  currentCalories = 0,
  calorieGoal = 2000,
  macros = { protein: 0, carbs: 0, fat: 0 },
  currentWaterMl = 0,
  waterGoalMl = 2000,
  proteinGoalG = 0,
  carbGoalG = 0,
  fatGoalG = 0
}) {
  const insights = [];
  const hour = new Date().getHours();

  // Time-based greeting
  let greeting;
  if (hour < 12) greeting = generateMorningInsight(currentCalories, calorieGoal);
  else if (hour < 17) greeting = generateAfternoonInsight(currentCalories, calorieGoal);
  else greeting = generateEveningInsight(currentCalories, calorieGoal);
  if (greeting) insights.push(greeting);

  // Macro analysis
  const macroInsight = generateMacroInsight(macros, calorieGoal, proteinGoalG, carbGoalG, fatGoalG);
  if (macroInsight) insights.push(macroInsight);

  // Hydration
  const waterInsight = generateWaterInsight(currentWaterMl, waterGoalMl);
  if (waterInsight) insights.push(waterInsight);

  return insights.slice(0, 3);
}

/**
 * Get a smart swap suggestion for a food item.
 * @param {Object} food - { name, kcalPer100g, proteinPer100g, fatPer100g }
 * @returns {Object|null} CoachInsight or null
 */
function getSuggestionForFood(food) {
  const nameLower = food.name.toLowerCase();

  // 1. Check swap map
  for (const [keyword, swap] of Object.entries(SWAP_MAP)) {
    if (nameLower.includes(keyword)) {
      return {
        emoji: '🔄',
        message: `Try "${swap.alternative}" instead — ${swap.reason}`,
        type: InsightType.TIP,
        actionLabel: 'Swap it',
        actionData: swap.alternative
      };
    }
  }

  // 2. Generic: high calorie + low protein
  if (food.kcalPer100g > 350 && food.proteinPer100g < 8) {
    return {
      emoji: '💡',
      message: `This is calorie-dense (${food.kcalPer100g} kcal/100g) with low protein. Consider a high-protein alternative!`,
      type: InsightType.WARNING
    };
  }

  // 3. High fat warning
  if (food.fatPer100g > 25 && food.proteinPer100g < 10) {
    return {
      emoji: '🫒',
      message: `High fat content (${food.fatPer100g}g/100g). A grilled or baked version would cut fat significantly.`,
      type: InsightType.TIP
    };
  }

  return null;
}

module.exports = { generateInsights, getSuggestionForFood, InsightType };
