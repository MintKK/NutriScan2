import { useState, useEffect, useRef } from 'react';
import * as tf from '@tensorflow/tfjs-core';
import '@tensorflow/tfjs-backend-cpu';
import '@tensorflow/tfjs-backend-wasm';
// NOTE: We dynamically import @tensorflow/tfjs-tflite below to control
// when the WASM client initializes. Do NOT add a static import here.
import type { TFLiteModel } from '@tensorflow/tfjs-tflite';

const MIN_CONFIDENCE = 0.05;
const INPUT_SIZE = 192;

export interface MLResult {
  label: string;
  confidence: number;
}

export function useFoodClassifier() {
  const [isReady, setIsReady] = useState(false);
  const [labels, setLabels] = useState<string[]>([]);
  const modelRef = useRef<TFLiteModel | null>(null);

  useEffect(() => {
    async function init() {
      try {
        // 1. Initialize TF.js WASM Backend (much faster than CPU in browser)
        await tf.setBackend('wasm');
        await tf.ready();

        // 2. Dynamically import @tensorflow/tfjs-tflite.
        //    The WASM client inside this package eagerly loads .wasm files on
        //    import, so we must set the path FIRST, then import.
        const tflite = await import('@tensorflow/tfjs-tflite');
        tflite.setWasmPath('/');
        
        // 3. Load the Model
        modelRef.current = await tflite.loadTFLiteModel('/ml/food11.tflite');
        
        // 4. Load Labels
        const res = await fetch('/ml/food11_labels.txt');
        const text = await res.text();
        const loadedLabels = text.split('\n').map(l => l.trim()).filter(l => l.length > 0);
        setLabels(loadedLabels);
        
        setIsReady(true);
        console.log('[useFoodClassifier] TF.js WASM loaded successfully');
      } catch (err) {
        console.error('[useFoodClassifier] Failed to init TF.js:', err);
      }
    }
    init();
  }, []);

  const classifyImage = async (imageElement: HTMLImageElement): Promise<MLResult[]> => {
    if (!modelRef.current || labels.length === 0) {
      throw new Error('Model not loaded yet');
    }

    // 1. Preprocess: Resize image to 192x192 and extract tensor
    const tensor = tf.tidy(() => {
      const img = tf.browser.fromPixels(imageElement);
      // Resize with billinear interpolation
      const resized = tf.image.resizeBilinear(img, [INPUT_SIZE, INPUT_SIZE]);
      // Expand dims to [1, 192, 192, 3] and cast to int32
      const expanded = tf.expandDims(resized, 0);
      return tf.cast(expanded, 'int32');
    });

    // 2. Run Inference
    const outputTensor = modelRef.current.predict(tensor) as tf.Tensor;
    const scores = await outputTensor.data();

    // 3. Cleanup tensors
    tensor.dispose();
    outputTensor.dispose();

    // 4. Apply softmax to convert raw logits → 0-1 probabilities
    const rawScores = Array.from(scores) as number[];
    const maxScore = Math.max(...rawScores);
    const exps = rawScores.map(s => Math.exp(s - maxScore)); // subtract max for numerical stability
    const sumExps = exps.reduce((a, b) => a + b, 0);
    const probabilities = exps.map(e => e / sumExps);

    // 5. Parse Results
    const results = probabilities
      .map((confidence, index) => {
        const label = index < labels.length ? labels[index] : `Unknown_${index}`;
        if (label === '__background__' || label.startsWith('/g/') || label.startsWith('/m/')) {
          return null;
        }
        return { label, confidence };
      })
      .filter((r): r is MLResult => r !== null && r.confidence >= MIN_CONFIDENCE)
      .sort((a, b) => b.confidence - a.confidence)
      .slice(0, 10);

    return results;
  };

  return { isReady, classifyImage };
}
