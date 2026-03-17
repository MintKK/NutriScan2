/**
 * Nutrition Calculator — Direct port of NutritionCalculator.kt
 * Calculates personalized TDEE and macro targets using Mifflin-St Jeor equation.
 */

const ActivityLevel = {
  SEDENTARY: 'SEDENTARY',
  LIGHTLY_ACTIVE: 'LIGHTLY_ACTIVE',
  MODERATELY_ACTIVE: 'MODERATELY_ACTIVE',
  VERY_ACTIVE: 'VERY_ACTIVE'
};

const Goal = {
  FAT_LOSS: 'FAT_LOSS',
  MUSCLE_GAIN: 'MUSCLE_GAIN',
  WEIGHT_MAINTENANCE: 'WEIGHT_MAINTENANCE'
};

const Gender = {
  MALE: 'MALE',
  FEMALE: 'FEMALE'
};

const ACTIVITY_MULTIPLIERS = {
  [ActivityLevel.SEDENTARY]: 1.2,
  [ActivityLevel.LIGHTLY_ACTIVE]: 1.375,
  [ActivityLevel.MODERATELY_ACTIVE]: 1.55,
  [ActivityLevel.VERY_ACTIVE]: 1.725
};

const ACTIVITY_PROTEIN_MULTIPLIERS = {
  [ActivityLevel.SEDENTARY]: 1.0,
  [ActivityLevel.LIGHTLY_ACTIVE]: 1.1,
  [ActivityLevel.MODERATELY_ACTIVE]: 1.2,
  [ActivityLevel.VERY_ACTIVE]: 1.3
};

/**
 * Calculate nutrition targets from a user profile.
 * @param {Object} profile - { weightKg, heightCm, age, gender, activityLevel, goal }
 * @returns {Object} { calories, proteinGrams, carbGrams, fatGrams }
 */
function calculateTargets(profile) {
  const { weightKg, heightCm, age, gender, activityLevel, goal } = profile;

  if (weightKg <= 0) throw new Error('Weight must be positive');
  if (heightCm <= 0) throw new Error('Height must be positive');
  if (age < 1 || age > 150) throw new Error('Age must be between 1 and 150');

  // Mifflin-St Jeor BMR
  const bmr = gender === Gender.MALE
    ? (10 * weightKg) + (6.25 * heightCm) - (5 * age) + 5
    : (10 * weightKg) + (6.25 * heightCm) - (5 * age) - 161;

  const tdee = bmr * (ACTIVITY_MULTIPLIERS[activityLevel] || 1.2);

  // Goal-based calorie adjustment
  let calories;
  switch (goal) {
    case Goal.FAT_LOSS: calories = Math.round(tdee * 0.8); break;
    case Goal.MUSCLE_GAIN: calories = Math.round(tdee * 1.2); break;
    default: calories = Math.round(tdee);
  }

  // Macro splits
  const actProteinMult = ACTIVITY_PROTEIN_MULTIPLIERS[activityLevel] || 1.0;

  let proteinGrams;
  switch (goal) {
    case Goal.FAT_LOSS: proteinGrams = Math.round(weightKg * 2.0 * actProteinMult); break;
    case Goal.MUSCLE_GAIN: proteinGrams = Math.round(weightKg * 2.5 * actProteinMult); break;
    default: proteinGrams = Math.round(weightKg * 1.8 * actProteinMult);
  }

  const fatGrams = Math.round(calories * 0.25 / 9);
  const carbGrams = Math.max(0, Math.round((calories - (proteinGrams * 4) - (fatGrams * 9)) / 4));

  return { calories, proteinGrams, carbGrams, fatGrams };
}

/**
 * Calculate nutrition for a given food item and portion weight.
 * Port of CalculateNutritionUseCase.kt
 * @param {Object} food - { kcalPer100g, proteinPer100g, carbsPer100g, fatPer100g }
 * @param {number} grams - Portion weight in grams
 */
function calculatePortionNutrition(food, grams) {
  const factor = grams / 100;
  return {
    kcal: Math.round(food.kcalPer100g * factor),
    protein: parseFloat((food.proteinPer100g * factor).toFixed(1)),
    carbs: parseFloat((food.carbsPer100g * factor).toFixed(1)),
    fat: parseFloat((food.fatPer100g * factor).toFixed(1))
  };
}

module.exports = {
  calculateTargets,
  calculatePortionNutrition,
  ActivityLevel,
  Goal,
  Gender
};
