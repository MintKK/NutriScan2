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
    // Try to load @tensorflow/tfjs-node
    const tf = require('@tensorflow/tfjs-node');
    
    // Convert TFLite to TF.js format or use tfjs-tflite
    // For now, we'll check if a converted model exists
    const savedModelDir = path.join(path.dirname(modelPath), 'food11_tfjs');
    
    if (fs.existsSync(savedModelDir)) {
      interpreter = await tf.node.loadSavedModel(savedModelDir);
      tfAvailable = true;
      console.log('[FoodClassifier] TF.js model loaded successfully');
    } else {
      console.log('[FoodClassifier] No TF.js model found. Using label-based matching mode.');
      console.log('[FoodClassifier] To enable full inference, convert model: tensorflowjs_converter --input_format=tf_lite food11.tflite food11_tfjs/');
      tfAvailable = false;
    }
  } catch (e) {
    console.log(`[FoodClassifier] TF.js not available (${e.message}). Using label-based matching mode.`);
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

  const tf = require('@tensorflow/tfjs-node');
  
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
    if (!isInitialized) {
      return { results: [], status: 'ERROR', message: 'Classifier not initialized' };
    }

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
