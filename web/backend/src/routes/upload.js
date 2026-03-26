/**
 * Upload Route — POST /api/upload
 * Accepts an image file, saves it to local uploads directory,
 * and returns a URL for the uploaded file.
 */

const express = require('express');
const multer = require('multer');
const sharp = require('sharp');
const path = require('path');
const fs = require('fs');
const { v4: uuidv4 } = require('uuid');

const router = express.Router();

// Ensure uploads directory exists
const UPLOADS_DIR = path.resolve(__dirname, '../../uploads');
if (!fs.existsSync(UPLOADS_DIR)) {
  fs.mkdirSync(UPLOADS_DIR, { recursive: true });
}

// Multer config: in-memory storage for processing with sharp
const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 10 * 1024 * 1024 }, // 10MB max
  fileFilter: (req, file, cb) => {
    const allowed = ['image/jpeg', 'image/png', 'image/webp'];
    if (allowed.includes(file.mimetype)) {
      cb(null, true);
    } else {
      cb(new Error('Only JPEG, PNG, and WebP images are allowed'));
    }
  }
});

/**
 * POST /api/upload
 * Accepts multipart form data with a single "image" field.
 * Resizes and compresses the image, saves to uploads/, returns URL.
 */
router.post('/', upload.single('image'), async (req, res) => {
  try {
    if (!req.file) {
      return res.status(400).json({ error: 'No image file provided' });
    }

    const filename = `uploads/${uuidv4()}.webp`;

    // Resize and compress with sharp
    const processedImageBuffer = await sharp(req.file.buffer)
      .resize(800, 800, { fit: 'inside', withoutEnlargement: true })
      .webp({ quality: 80 })
      .toBuffer();

    // Upload to Firebase Storage (GCS)
    const admin = require('firebase-admin');
    const bucket = admin.storage().bucket();
    const file = bucket.file(filename);

    await file.save(processedImageBuffer, {
      metadata: {
        contentType: 'image/webp',
        cacheControl: 'public, max-age=31536000'
      }
    });

    // Make the file public to generate a regular Firebase Storage download URL
    await file.makePublic();
    const imageUrl = file.publicUrl();

    console.log(`[Upload] Saved image to GCS: ${filename} (${(processedImageBuffer.length / 1024).toFixed(1)}KB)`);

    res.json({ imageUrl, filename });
  } catch (error) {
    console.error('[Upload] Error:', error);
    res.status(500).json({ error: error.message || 'Upload failed' });
  }
});

module.exports = router;
