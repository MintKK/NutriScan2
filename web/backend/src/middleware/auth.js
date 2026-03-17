/**
 * Firebase Auth Middleware
 * Validates Firebase ID tokens from the Authorization header.
 * Attaches decoded user to req.user.
 */

const admin = require('firebase-admin');

/**
 * Middleware that requires authentication.
 * Expects header: Authorization: Bearer <idToken>
 */
async function requireAuth(req, res, next) {
  const authHeader = req.headers.authorization;
  
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'Missing or invalid authorization header' });
  }

  const idToken = authHeader.split('Bearer ')[1];

  try {
    const decodedToken = await admin.auth().verifyIdToken(idToken);
    req.user = {
      uid: decodedToken.uid,
      email: decodedToken.email,
      name: decodedToken.name || ''
    };
    next();
  } catch (error) {
    console.error('[Auth] Token verification failed:', error.message);
    return res.status(401).json({ error: 'Invalid or expired token' });
  }
}

/**
 * Optional auth — attaches user if token present, continues either way.
 */
async function optionalAuth(req, res, next) {
  const authHeader = req.headers.authorization;
  
  if (authHeader && authHeader.startsWith('Bearer ')) {
    const idToken = authHeader.split('Bearer ')[1];
    try {
      const decodedToken = await admin.auth().verifyIdToken(idToken);
      req.user = {
        uid: decodedToken.uid,
        email: decodedToken.email,
        name: decodedToken.name || ''
      };
    } catch (e) {
      // Token invalid, proceed without user
      req.user = null;
    }
  } else {
    req.user = null;
  }
  next();
}

module.exports = { requireAuth, optionalAuth };
