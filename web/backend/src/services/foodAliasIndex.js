/**
 * Food Alias Index — Direct port of FoodAliasIndex.kt
 * Pre-processed in-memory index for O(1) food lookups.
 * Built once when foods are loaded from the JSON file.
 */

const { normalize } = require('./labelNormalizer');

class FoodAliasIndex {
  /**
   * @param {Array} foods - Array of food objects from food_items.json
   */
  constructor(foods) {
    this.nameMap = new Map();   // normalized name → food
    this.aliasMap = new Map();  // normalized alias → food
    this.allFoods = foods;

    for (const food of foods) {
      const normalizedName = normalize(food.name);
      if (normalizedName) {
        this.nameMap.set(normalizedName, food);
      }

      if (food.aliases) {
        const aliases = food.aliases.split(',');
        for (const alias of aliases) {
          const normalizedAlias = normalize(alias.trim());
          if (normalizedAlias && normalizedAlias !== normalizedName) {
            this.aliasMap.set(normalizedAlias, food);
          }
        }
      }
    }
  }

  /** Find food by exact normalized name match. */
  findByExactName(normalizedQuery) {
    return this.nameMap.get(normalizedQuery) || null;
  }

  /** Find food by alias match. */
  findByAlias(normalizedQuery) {
    return this.aliasMap.get(normalizedQuery) || null;
  }

  /**
   * Find food by partial name match (contains).
   * Minimum query length enforced to prevent "ham" → "hamburger".
   */
  findByPartialName(normalizedQuery, minLength = 4) {
    if (normalizedQuery.length < minLength) return null;

    // Check names first
    for (const [name, food] of this.nameMap) {
      if (name.includes(normalizedQuery) || normalizedQuery.includes(name)) {
        return food;
      }
    }

    // Then check aliases
    for (const [alias, food] of this.aliasMap) {
      if (alias.includes(normalizedQuery) || normalizedQuery.includes(alias)) {
        return food;
      }
    }

    return null;
  }

  isEmpty() { return this.nameMap.size === 0; }
  size() { return this.nameMap.size; }
}

module.exports = FoodAliasIndex;
