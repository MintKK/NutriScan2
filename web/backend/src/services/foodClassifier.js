/**
 * Food Classifier — Port of TFLiteFoodClassifier.kt
 * Uses sharp for image preprocessing and the food11 model for classification.
 * 
 * Since @tensorflow/tfjs-node can be complex to install, this service provides
 * a fallback mode that returns the top food matches via keyword search when
 * TF.js is unavailable. For production, TF.js should be properly installed.
 */

const fs = require('fs');
const path = require('path');
const sharp = require('sharp');

const MIN_CONFIDENCE = 0.05;
const HIGH_CONFIDENCE = 0.7;
const INPUT_SIZE = 192;

let interpreter = null;
let labels = [];
let isInitialized = false;
let tfAvailable = false;

/**
 * Load labels from the food11_labels.txt file.
 */
function loadLabels(labelsPath) {
  const content = fs.readFileSync(labelsPath, 'utf-8');
  labels = content.split('\n').filter(l => l.trim().length > 0);
  console.log(`[FoodClassifier] Loaded ${labels.length} labels`);
  return labels;
}

/**
 * Try to initialize TensorFlow.js with the TFLite model.
 */
async function initialize(modelPath, labelsPath) {
  if (isInitialized) return;

  loadLabels(labelsPath);

  try {
    // Import TF.js core and the TFLite runner
    const tf = require('@tensorflow/tfjs-core');
    require('@tensorflow/tfjs-backend-cpu'); // CPU backend is most reliable in Node
    
    // tfjs-tflite expects a browser environment.
    // We must polyfill `self` before requiring it, or the WASM loader crashes.
    if (typeof self === 'undefined') {
      global.self = global;
    }
    const tflite = require('@tensorflow/tfjs-tflite');
    
    // Set backend to CPU explicitly
    await tf.setBackend('cpu');
    await tf.ready();
    console.log('[FoodClassifier] TF.js CPU backend initialized');

    // In Node.js, loadTFLiteModel expects a URL and fetch() fails for local paths.
    // So we read the file directly into a buffer and pass the buffer.
    const modelBuffer = fs.readFileSync(modelPath);
    const modelArrayBuffer = new Uint8Array(modelBuffer).buffer;

    // Load the raw TFLite model from the buffer
    interpreter = await tflite.loadTFLiteModel(modelArrayBuffer);
    tfAvailable = true;
    console.log('[FoodClassifier] TFLite model loaded successfully');
  } catch (e) {
    console.log(`[FoodClassifier] ML model failed to load (${e.message}). Using label-based matching mode.`);
    tfAvailable = false;
  }

  isInitialized = true;
}

/**
 * Preprocess an image buffer for model input.
 * Resize to 192x192, extract raw RGB pixel bytes.
 */
async function preprocessImage(imageBuffer) {
  const { data, info } = await sharp(imageBuffer)
    .resize(INPUT_SIZE, INPUT_SIZE, { fit: 'cover' })
    .removeAlpha()
    .raw()
    .toBuffer({ resolveWithObject: true });

  return { pixels: data, width: info.width, height: info.height, channels: info.channels };
}

/**
 * Run TF.js inference on preprocessed image data.
 */
async function runInference(pixels) {
  if (!tfAvailable || !interpreter) {
    return null;
  }

  const tf = require('@tensorflow/tfjs-core');
  
  // Create input tensor [1, 192, 192, 3] as UINT8
  const inputTensor = tf.tensor4d(
    Array.from(pixels),
    [1, INPUT_SIZE, INPUT_SIZE, 3],
    'int32'
  );

  const output = interpreter.predict(inputTensor);
  const scores = await output.data();
  
  inputTensor.dispose();
  output.dispose();

  return Array.from(scores);
}

/**
 * Parse model output scores into classification results.
 * Filters out background and knowledge graph labels.
 */
function parseOutput(scores) {
  return scores
    .map((confidence, index) => {
      const label = index < labels.length ? labels[index] : `Unknown_${index}`;
      // Filter out background and Knowledge Graph IDs
      if (label === '__background__' || label.startsWith('/g/') || label.startsWith('/m/')) {
        return null;
      }
      return { label, confidence, index };
    })
    .filter(r => r !== null && r.confidence >= MIN_CONFIDENCE)
    .sort((a, b) => b.confidence - a.confidence)
    .slice(0, 10);
}

/**
 * Classify a food image.
 * @param {Buffer} imageBuffer - Raw image file buffer
 * @returns {Object} { results, status }
 */
async function classifyFood(imageBuffer) {
  try {
    if (!isInitialized && !process.env.INFERENCE_SERVICE_URL) {
      return { results: [], status: 'ERROR', message: 'Classifier not initialized' };
    }

    // Attempt to use the dedicated ML Inference Microservice if configured
    if (process.env.INFERENCE_SERVICE_URL) {
      try {
        const formData = new FormData();
        // Use File instead of Blob — more reliable for multipart uploads in Node.js 20
        const file = new File([imageBuffer], 'image.jpg', { type: 'image/jpeg' });
        formData.append('image', file);

        const inferenceUrl = `${process.env.INFERENCE_SERVICE_URL}/predict`;
        console.log(`[FoodClassifier] Calling inference service: ${inferenceUrl} (${imageBuffer.length} bytes)`);

        const response = await fetch(inferenceUrl, {
          method: 'POST',
          body: formData,
        });

        if (response.ok) {
          const data = await response.json();
          const results = data.results || [];
          console.log(`[FoodClassifier] Inference returned ${results.length} results`);
          if (results.length === 0) return { results: [], status: 'NO_FOOD_DETECTED' };
          
          const status = results[0].confidence >= HIGH_CONFIDENCE
            ? 'HIGH_CONFIDENCE'
            : results.length === 1 ? 'SINGLE_MATCH' : 'MULTIPLE_CANDIDATES';
            
          return { results, status };
        } else {
          // Log the actual error body for debugging
          const errBody = await response.text().catch(() => 'no body');
          console.error(`[FoodClassifier] Inference Service returned ${response.status}: ${errBody}`);
          return {
            results: [],
            status: 'ERROR',
            message: `Inference service error (${response.status}). Please use manual food search.`
          };
        }
      } catch (err) {
        console.error('[FoodClassifier] Failed to reach Inference Service:', err.message, err.stack);
        return {
          results: [],
          status: 'ERROR',
          message: 'Could not reach ML service. Please use manual food search.'
        };
      }
    }

    // Fallback to local TF.js implementation
    const { pixels } = await preprocessImage(imageBuffer);

    if (tfAvailable) {
      const scores = await runInference(pixels);
      if (!scores) {
        return { results: [], status: 'ERROR', message: 'Inference failed' };
      }

      const results = parseOutput(scores);
      
      if (results.length === 0) {
        return { results: [], status: 'NO_FOOD_DETECTED' };
      }

      const status = results[0].confidence >= HIGH_CONFIDENCE
        ? 'HIGH_CONFIDENCE'
        : results.length === 1 ? 'SINGLE_MATCH' : 'MULTIPLE_CANDIDATES';

      return { results, status };
    } else {
      // Fallback: return empty results, frontend will show manual search
      return {
        results: [],
        status: 'NO_MODEL',
        message: 'ML model not loaded. Please use manual food search.'
      };
    }
  } catch (e) {
    console.error('[FoodClassifier] Error:', e.message);
    return { results: [], status: 'ERROR', message: e.message };
  }
}

/**
 * Get all available labels (for manual search support).
 */
function getLabels() {
  return labels.filter(l => l !== '__background__' && !l.startsWith('/g/') && !l.startsWith('/m/'));
}

module.exports = { initialize, classifyFood, preprocessImage, getLabels, loadLabels };
