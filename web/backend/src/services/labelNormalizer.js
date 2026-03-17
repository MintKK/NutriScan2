/**
 * Label Normalizer — Direct port of LabelNormalizer.kt
 * Normalizes ML classification labels for database matching.
 * Handles plurals, stopwords, separators, and case normalization.
 */

const STOPWORDS = new Set([
  'food', 'dish', 'meal', 'cuisine', 'snack', 'produce',
  'fresh', 'cooked', 'raw', 'fried', 'baked', 'grilled',
  'organic', 'natural', 'healthy', 'delicious'
]);

// Plural → singular mappings (checked in order)
const PLURAL_RULES = [
  ['ies', 'y'],    // berries → berry
  ['ves', 'f'],    // leaves → leaf
  ['oes', 'o'],    // tomatoes → tomato
  ['es', ''],      // dishes → dish
  ['s', '']        // apples → apple
];

/**
 * Convert plural word to singular.
 * Only applies to words with minimum length to avoid false positives.
 */
function singularize(word) {
  if (word.length < 4) return word;

  // Special case: words like "apples", "noodles", "pickles" where removing
  // "es" incorrectly yields "appl", "noodl", "pickl"
  if (word.endsWith('les') && word.length >= 5) {
    const charBeforeLes = word[word.length - 4];
    if (!'aeiou'.includes(charBeforeLes)) {
      return word.slice(0, -1); // "apples" → "apple"
    }
  }

  for (const [suffix, replacement] of PLURAL_RULES) {
    if (word.endsWith(suffix)) {
      const stem = word.slice(0, -suffix.length);
      if (stem.length >= 2) {
        return stem + replacement;
      }
    }
  }
  return word;
}

/**
 * Normalize a single ML label for database matching.
 * "Granny Smith Apples" → "granny smith apple"
 */
function normalize(label) {
  return label
    .toLowerCase()
    .replace(/[_\-]/g, ' ')           // Convert separators to spaces
    .replace(/[^a-z\s]/g, '')         // Remove non-alpha characters
    .split(/\s+/)
    .filter(w => w.length > 0 && !STOPWORDS.has(w))
    .map(w => singularize(w))
    .join(' ')
    .trim();
}

/**
 * Extract search tokens from a normalized label.
 * Prioritizes full phrase, then last word (usually the food type).
 * "granny smith apple" → ["granny smith apple", "apple", "granny smith"]
 */
function extractTokens(normalized) {
  if (!normalized || !normalized.trim()) return [];

  const words = normalized.split(' ').filter(w => w.length > 0);
  const tokens = [normalized];

  if (words.length > 1) {
    tokens.push(words[words.length - 1]); // Last word
    tokens.push(words.slice(0, -1).join(' ')); // Prefix without last word
  }

  return [...new Set(tokens)];
}

module.exports = { normalize, extractTokens, singularize };
