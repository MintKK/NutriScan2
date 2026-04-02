/**
 * useFoodClassifier — Cloud-based food classification hook
 * 
 * Instead of running TFLite locally in the browser (which has WASM compatibility
 * issues with Vite 8+), this hook sends the image to the backend API, which
 * forwards it to the Cloud Run ML inference service.
 * 
 * This is architecturally better for a cloud deployment:
 * - ML runs on a dedicated, GPU-capable container
 * - No 20MB model download to the browser
 * - Consistent results across all devices
 * - Model updates don't require frontend redeployment
 */
import { useState } from 'react';
import { classifyApi } from '../services/api';

export interface FoodCandidate {
  food: { name: string; kcalPer100g: number; proteinPer100g: number; carbsPer100g: number; fatPer100g: number };
  confidence: number;
  combinedScore: number;
  mlLabel: string;
  matchType: string;
  portions: Record<string, { kcal: number; protein: number; carbs: number; fat: number }>;
  coachTip: { emoji: string; message: string } | null;
}

export interface ClassifyResult {
  status: string;
  message?: string;
  candidates: FoodCandidate[];
}

export function useFoodClassifier() {
  const [isClassifying, setIsClassifying] = useState(false);

  /**
   * Sends an image file to the backend for ML classification.
   * The backend forwards it to the Cloud Run inference service,
   * matches results against the food database, and returns candidates.
   */
  const classifyImage = async (file: File): Promise<ClassifyResult> => {
    setIsClassifying(true);
    try {
      const res = await classifyApi.classifyImage(file);
      return res.data;
    } catch (err: any) {
      console.error('[useFoodClassifier] Classification failed:', err);
      return {
        status: 'ERROR',
        message: err?.response?.data?.message || 'Classification failed. Try searching manually.',
        candidates: []
      };
    } finally {
      setIsClassifying(false);
    }
  };

  return { isClassifying, classifyImage };
}
