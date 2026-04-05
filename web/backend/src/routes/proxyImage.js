/**
 * Image Proxy Route
 * Fetches an external image server-side to bypass CORS restrictions.
 * GET /api/proxy-image?url=<encoded-url>
 */

const express = require('express');
const router = express.Router();
const https = require('https');
const http = require('http');

router.get('/', (req, res) => {
  const imageUrl = req.query.url;
  if (!imageUrl) {
    return res.status(400).json({ error: 'Missing "url" query parameter' });
  }

  // Basic validation
  let parsedUrl;
  try {
    parsedUrl = new URL(imageUrl);
  } catch {
    return res.status(400).json({ error: 'Invalid URL' });
  }

  if (!['http:', 'https:'].includes(parsedUrl.protocol)) {
    return res.status(400).json({ error: 'Only HTTP/HTTPS URLs are allowed' });
  }

  const client = parsedUrl.protocol === 'https:' ? https : http;

  const proxyReq = client.get(imageUrl, { headers: { 'User-Agent': 'NutriScan/1.0' } }, (proxyRes) => {
    // Follow redirects (up to 3)
    if ([301, 302, 307, 308].includes(proxyRes.statusCode) && proxyRes.headers.location) {
      const redirectUrl = proxyRes.headers.location;
      const redirectClient = redirectUrl.startsWith('https') ? https : http;
      redirectClient.get(redirectUrl, { headers: { 'User-Agent': 'NutriScan/1.0' } }, (redirectRes) => {
        const contentType = redirectRes.headers['content-type'] || 'image/jpeg';
        res.setHeader('Content-Type', contentType);
        res.setHeader('Cache-Control', 'public, max-age=86400');
        res.setHeader('Access-Control-Allow-Origin', '*');
        redirectRes.pipe(res);
      }).on('error', () => {
        res.status(502).json({ error: 'Failed to fetch redirected image' });
      });
      return;
    }

    if (proxyRes.statusCode !== 200) {
      return res.status(proxyRes.statusCode).json({ error: `Remote server returned ${proxyRes.statusCode}` });
    }

    const contentType = proxyRes.headers['content-type'] || 'image/jpeg';
    if (!contentType.startsWith('image/')) {
      return res.status(400).json({ error: 'URL does not point to an image' });
    }

    res.setHeader('Content-Type', contentType);
    res.setHeader('Cache-Control', 'public, max-age=86400');
    res.setHeader('Access-Control-Allow-Origin', '*');
    proxyRes.pipe(res);
  });

  proxyReq.on('error', () => {
    if (!res.headersSent) {
      res.status(502).json({ error: 'Failed to fetch image from URL' });
    }
  });

  // 10 second timeout
  proxyReq.setTimeout(10000, () => {
    proxyReq.destroy();
    if (!res.headersSent) {
      res.status(504).json({ error: 'Image fetch timed out' });
    }
  });
});

module.exports = router;
