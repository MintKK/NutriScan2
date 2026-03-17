/**
 * Food Matching Service — Direct port of FoodMatchingService.kt + Food11CategoryMapper.kt
 * Matches ML classification results against the food database.
 * 4-strategy ranked matching: exact → alias → token → partial.
 */

const { normalize, extractTokens } = require('./labelNormalizer');

// Match type scores (higher = more trustworthy)
const MatchType = {
  EXACT: { name: 'EXACT', score: 100 },
  ALIAS: { name: 'ALIAS', score: 80 },
  TOKEN: { name: 'TOKEN', score: 60 },
  PARTIAL: { name: 'PARTIAL', score: 30 },
  NONE: { name: 'NONE', score: 0 }
};

// Food-11 category → specific food expansion (from Food11CategoryMapper.kt)
const CATEGORY_TO_FOODS = {
  'Bread': ['bread', 'toast', 'bagel', 'croissant', 'baguette', 'roll', 'bun', 'muffin', 'pretzel'],
  'Dairy product': ['milk', 'cheese', 'yogurt', 'butter', 'cream', 'ice cream', 'cottage cheese', 'mozzarella'],
  'Dessert': ['cake', 'pie', 'cookie', 'brownie', 'donut', 'doughnut', 'pastry', 'cupcake', 'cheesecake', 'pudding', 'chocolate'],
  'Egg': ['egg', 'eggs', 'omelette', 'omelet', 'scrambled eggs', 'fried egg', 'boiled egg', 'poached egg'],
  'Fried food': ['french fries', 'fries', 'fried chicken', 'chicken nuggets', 'onion rings', 'tempura', 'fried', 'crispy'],
  'Meat': ['beef', 'steak', 'pork', 'lamb', 'chicken', 'turkey', 'sausage', 'bacon', 'ham', 'burger', 'hamburger', 'meatball'],
  'Noodles-Pasta': ['pasta', 'spaghetti', 'noodles', 'ramen', 'udon', 'pho', 'lasagna', 'macaroni', 'fettuccine', 'penne'],
  'Rice': ['rice', 'fried rice', 'risotto', 'sushi', 'paella', 'biryani', 'pilaf'],
  'Seafood': ['fish', 'salmon', 'tuna', 'shrimp', 'prawn', 'lobster', 'crab', 'oyster', 'squid', 'clam', 'mussel'],
  'Soup': ['soup', 'broth', 'stew', 'chowder', 'bisque', 'pho', 'ramen', 'miso'],
  'Vegetable-Fruit': ['apple', 'banana', 'orange', 'grape', 'strawberry', 'carrot', 'broccoli', 'tomato', 'salad', 'vegetable', 'fruit']
};

/**
 * Get search terms for a Food-11 category.
 */
function getCategorySearchTerms(category) {
  // Exact match
  if (CATEGORY_TO_FOODS[category]) return CATEGORY_TO_FOODS[category];

  // Normalized match
  const trimmed = category.trim();
  for (const [key, terms] of Object.entries(CATEGORY_TO_FOODS)) {
    if (key.toLowerCase() === trimmed.toLowerCase()) return terms;
    if (key.replace('-', '/').toLowerCase() === trimmed.replace('-', '/').toLowerCase()) return terms;
  }

  return [category.toLowerCase()];
}

function isKnownCategory(category) {
  return getCategorySearchTerms(category).length > 1;
}

/**
 * Create a FoodMatchResult object.
 */
function createMatchResult(mlLabel, normalizedLabel, confidence, matchedFood, matchType) {
  const combinedScore = matchedFood
    ? confidence * (matchType.score / 100)
    : 0;

  return {
    mlLabel,
    normalizedLabel,
    confidence,
    matchedFood,
    matchType: matchType.name,
    matchTypeScore: matchType.score,
    combinedScore,
    confidencePercent: Math.round(confidence * 100),
    isValidCandidate: matchedFood != null && matchType !== MatchType.NONE,
    isSafeForAutoSelect: matchedFood != null &&
      (matchType === MatchType.EXACT || matchType === MatchType.ALIAS) &&
      confidence >= 0.5
  };
}

/**
 * Match a single ML label against the database.
 * Tries matching in order: exact → alias → token → partial
 */
function matchSingleLabel(label, confidence, index) {
  const normalized = normalize(label);
  if (normalized.length < 2) {
    return [createMatchResult(label, normalized, confidence, null, MatchType.NONE)];
  }

  const tokens = extractTokens(normalized);
  const matches = [];

  for (const token of tokens) {
    // 1. Exact name match (highest priority)
    const exactMatch = index.findByExactName(token);
    if (exactMatch) {
      return [createMatchResult(
        label, normalized, confidence, exactMatch,
        token === normalized ? MatchType.EXACT : MatchType.TOKEN
      )];
    }

    // 2. Alias match
    const aliasMatch = index.findByAlias(token);
    if (aliasMatch) {
      matches.push(createMatchResult(label, normalized, confidence, aliasMatch, MatchType.ALIAS));
    }
  }

  if (matches.length > 0) return matches;

  // 3. Fallback: partial match
  const partialMatch = index.findByPartialName(normalized);
  if (partialMatch) {
    return [createMatchResult(label, normalized, confidence, partialMatch, MatchType.PARTIAL)];
  }

  // 4. No match
  return [createMatchResult(label, normalized, confidence, null, MatchType.NONE)];
}

/**
 * Match a Food-11 category by searching for expanded terms.
 */
function matchCategoryLabel(category, confidence, searchTerms, index) {
  const matches = [];
  const seenIds = new Set();

  for (const term of searchTerms) {
    const exactMatch = index.findByExactName(term);
    if (exactMatch && !seenIds.has(exactMatch.name)) {
      seenIds.add(exactMatch.name);
      matches.push(createMatchResult(category, term, confidence * 0.95, exactMatch, MatchType.ALIAS));
    }

    const aliasMatch = index.findByAlias(term);
    if (aliasMatch && !seenIds.has(aliasMatch.name)) {
      seenIds.add(aliasMatch.name);
      matches.push(createMatchResult(category, term, confidence * 0.9, aliasMatch, MatchType.ALIAS));
    }
  }

  if (matches.length > 0) {
    return matches.sort((a, b) => b.combinedScore - a.combinedScore);
  }

  return [createMatchResult(category, category.toLowerCase(), confidence, null, MatchType.NONE)];
}

/**
 * Match ML classification results against the food database.
 * @param {Array} results - [{label, confidence, index}]
 * @param {FoodAliasIndex} foodIndex
 * @returns {Array} Ranked FoodMatchResults
 */
function matchClassifications(results, foodIndex) {
  const allMatches = results.flatMap(result => {
    const searchTerms = getCategorySearchTerms(result.label);

    if (searchTerms.length > 1 && isKnownCategory(result.label)) {
      return matchCategoryLabel(result.label, result.confidence, searchTerms, foodIndex);
    } else {
      return matchSingleLabel(result.label, result.confidence, foodIndex);
    }
  });

  // Sort by combined score, deduplicate by food name
  const seen = new Set();
  return allMatches
    .sort((a, b) => b.combinedScore - a.combinedScore)
    .filter(m => {
      const key = m.matchedFood ? m.matchedFood.name : m.mlLabel;
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    })
    .slice(0, 10);
}

function getBestAutoSelectMatch(results) {
  return results.find(r => r.isSafeForAutoSelect) || null;
}

function getValidCandidates(results) {
  return results.filter(r => r.isValidCandidate);
}

module.exports = {
  matchClassifications,
  getBestAutoSelectMatch,
  getValidCandidates,
  MatchType,
  getCategorySearchTerms,
  isKnownCategory
};
