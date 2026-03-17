// Stub for @tensorflow/tfjs-tflite's tflite_web_api_client.
// The library uses `import * as tfliteWebAPIClient from '../tflite_web_api_client'`
// and accesses tfliteWebAPIClient.tfweb.tflite_web_api.{setWasmPath, getWasmFeatures, ...}
// as well as tfliteWebAPIClient.tfweb.TFLiteWebModelRunner.create(...), etc.
// This stub provides the expected shape so the module can load without crashing.
// The actual WASM client is loaded at runtime from the CDN via setWasmPath.

const noop = () => {};
const asyncNoop = async () => {};

export const tfweb = {
  tflite_web_api: {
    setWasmPath: noop,
    getWasmFeatures: async () => ({ multiThreading: false }),
  },
  // Used by tflite_model.js → loadTFLiteModel()
  TFLiteWebModelRunner: {
    create: asyncNoop,
  },
  // Used by various task library clients (image_classifier, object_detector, etc.)
  ImageClassifierOptions: class {},
  ImageClassifier: { create: asyncNoop },
  ObjectDetectorOptions: class {},
  ObjectDetector: { create: asyncNoop },
  ImageSegmenterOptions: class {},
  ImageSegmenter: { create: asyncNoop },
  NLClassifier: { create: asyncNoop },
  BertNLClassifierOptions: class {},
  BertNLClassifier: { create: asyncNoop },
  BertQuestionAnswerer: { create: asyncNoop },
};

export default {};

