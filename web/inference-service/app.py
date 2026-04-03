import os
import ssl
import json
import logging
import numpy as np
from PIL import Image
from flask import Flask, request, jsonify

# Cloud Run health checks + initialization
app = Flask(__name__)
logging.basicConfig(level=logging.INFO)

# Load labels
LABELS_PATH = os.path.join(os.path.dirname(__file__), 'model', 'food11_labels.txt')
MODEL_PATH = os.path.join(os.path.dirname(__file__), 'model', 'food11.tflite')

try:
    with open(LABELS_PATH, 'r') as f:
        labels = [line.strip() for line in f if line.strip()]
    logging.info(f"Loaded {len(labels)} labels")
except Exception as e:
    labels = []
    logging.error(f"Failed to load labels: {e}")

# Try to load tflite runtime
try:
    import tflite_runtime.interpreter as tflite
    interpreter = tflite.Interpreter(model_path=MODEL_PATH)
    interpreter.allocate_tensors()
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    tf_available = True
    logging.info("TFLite model loaded successfully")
except Exception as e:
    tf_available = False
    logging.error(f"Failed to load TFLite: {e}")

MIN_CONFIDENCE = 0.05

def process_image(img):
    """Resize image to 192x192 and convert to INT32 tensor"""
    img = img.resize((192, 192)).convert('RGB')
    input_data = np.expand_dims(img, axis=0).astype(np.uint8)
    return input_data



@app.route('/predict', methods=['POST'])
def predict():
    if not tf_available:
        return jsonify({"error": "ML model not available"}), 503
        
    if 'image' not in request.files:
        return jsonify({"error": "No image provided"}), 400
        
    try:
        file = request.files['image']
        img = Image.open(file.stream)
        input_data = process_image(img)
        
        # Inference
        interpreter.set_tensor(input_details[0]['index'], input_data)
        interpreter.invoke()
        # Dequantize the output if it's uint8
        output_data = interpreter.get_tensor(output_details[0]['index'])
        scores = output_data[0]
        
        if output_details[0]['dtype'] == np.uint8:
            scale, zero_point = output_details[0].get('quantization', (0.0, 0))
            if scale and scale > 0:
                probs = (scores.astype(float) - zero_point) * scale
            else:
                probs = scores.astype(float) / 255.0
        else:
            probs = scores.astype(float)
        
        results = []
        for i, conf in enumerate(probs):
            if conf >= MIN_CONFIDENCE:
                label = labels[i] if i < len(labels) else f"Unknown_{i}"
                if not (label == '__background__' or label.startswith('/g/') or label.startswith('/m/')):
                    results.append({"label": label, "confidence": float(conf)})
                    
        results.sort(key=lambda x: x['confidence'], reverse=True)
        return jsonify({"results": results[:10]})
    except Exception as e:
        logging.error(f"Inference error: {e}")
        return jsonify({"error": str(e)}), 500

@app.route('/health', methods=['GET'])
def health():
    return jsonify({"status": "ok", "model_loaded": tf_available})

if __name__ == '__main__':
    port = int(os.environ.get('PORT', 8080))
    app.run(host='0.0.0.0', port=port)
