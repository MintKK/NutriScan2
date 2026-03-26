/**
 * Social Routes — Ports key SocialRepository.kt endpoints.
 * Posts, likes, comments, follow/unfollow, user search.
 */

const express = require('express');
const admin = require('firebase-admin');
const { requireAuth, optionalAuth } = require('../middleware/auth');

const router = express.Router();
const db = () => admin.firestore();

// ============ TRENDING SCORE ============

function calculateTrendingScore(likes, comments, timestampMs) {
  const hoursSincePost = (Date.now() - timestampMs) / (1000 * 60 * 60);
  const engagement = likes + (comments * 2);
  const decayFactor = hoursSincePost > 24 ? 1.8 : 1.5;
  return engagement / Math.pow(hoursSincePost + 2, decayFactor);
}

// ============ FEED ============

/**
 * GET /api/social/feed — Get all posts sorted by trending score
 */
router.get('/feed', optionalAuth, async (req, res) => {
  try {
    const limit = parseInt(req.query.limit) || 20;
    const sortBy = req.query.sort || 'trendingScore';

    const snapshot = await db().collection('posts')
      .limit(limit)
      .get();

    const posts = snapshot.docs
      .map(doc => ({ postID: doc.id, ...doc.data() }))
      .sort((a, b) => (b[sortBy] || 0) - (a[sortBy] || 0));

    // If authenticated, check which posts are liked
    if (req.user) {
      const likeChecks = posts.map(p =>
        db().collection('likes').doc(`${p.postID}_${req.user.uid}`).get()
      );
      const likeResults = await Promise.all(likeChecks);
      posts.forEach((p, i) => { p.isLiked = likeResults[i].exists; });
    }

    res.json({ posts });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// ============ POSTS ============

/**
 * POST /api/social/posts — Create a new post
 * Body: { caption, foodName, calories, protein, carbs, fat, foodImageUrl }
 */
router.post('/posts', requireAuth, async (req, res) => {
  try {
    const { caption, foodName, calories, protein, carbs, fat, foodImageUrl } = req.body;

    // Get user profile (auto-create if it doesn't exist)
    const userDoc = await db().collection('users').doc(req.user.uid).get();
    let userProfile;
    if (!userDoc.exists) {
      // Auto-create a basic profile from Firebase Auth info
      userProfile = {
        uid: req.user.uid,
        email: req.user.email || '',
        username: (req.user.email || 'user').split('@')[0],
        displayname: '',
        bio: '',
        profileImageUrl: '',
        numFollowers: 0,
        numFollowing: 0,
        numPosts: 0,
        created: Date.now(),
        updated: Date.now()
      };
      await db().collection('users').doc(req.user.uid).set(userProfile);
    } else {
      userProfile = userDoc.data();
    }

    const postRef = db().collection('posts').doc();
    const post = {
      postID: postRef.id,
      userID: req.user.uid,
      username: userProfile.username || '',
      userProfileImageUrl: userProfile.profileImageUrl || '',
      caption: caption || '',
      foodImageUrl: foodImageUrl || '',
      foodName: foodName || '',
      numCalories: calories || 0,
      numProtein: protein || 0,
      numCarbs: carbs || 0,
      numFat: fat || 0,
      numLikes: 0,
      numComments: 0,
      created: Date.now(),
      trendingScore: calculateTrendingScore(0, 0, Date.now())
    };

    await postRef.set(post);

    // Increment user post count
    await db().collection('users').doc(req.user.uid)
      .update({ numPosts: admin.firestore.FieldValue.increment(1) });

    res.status(201).json(post);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

/**
 * DELETE /api/social/posts/:id — Delete a post
 */
router.delete('/posts/:id', requireAuth, async (req, res) => {
  try {
    const postRef = db().collection('posts').doc(req.params.id);
    const postDoc = await postRef.get();

    if (!postDoc.exists) return res.status(404).json({ error: 'Post not found' });

    const post = postDoc.data();
    if (post.userID !== req.user.uid) {
      return res.status(403).json({ error: 'Not authorized' });
    }

    await db().runTransaction(async (t) => {
      t.delete(postRef);
      t.update(db().collection('users').doc(post.userID), {
        numPosts: admin.firestore.FieldValue.increment(-1)
      });
    });

    res.json({ success: true });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// ============ LIKES ============

/**
 * POST /api/social/posts/:id/like — Toggle like
 */
router.post('/posts/:id/like', requireAuth, async (req, res) => {
  try {
    const postID = req.params.id;
    const likeID = `${postID}_${req.user.uid}`;
    const likeRef = db().collection('likes').doc(likeID);
    const likeDoc = await likeRef.get();

    if (likeDoc.exists) {
      // Unlike
      await likeRef.delete();
      await db().collection('posts').doc(postID)
        .update({ numLikes: admin.firestore.FieldValue.increment(-1) });
      res.json({ liked: false });
    } else {
      // Like
      await likeRef.set({
        likeID,
        postID,
        userID: req.user.uid,
        created: Date.now()
      });
      await db().collection('posts').doc(postID)
        .update({ numLikes: admin.firestore.FieldValue.increment(1) });
      res.json({ liked: true });
    }
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// ============ COMMENTS ============

/**
 * GET /api/social/posts/:id/comments — Get comments for a post
 */
router.get('/posts/:id/comments', async (req, res) => {
  try {
    const snapshot = await db().collection('comments')
      .where('postID', '==', req.params.id)
      .get();

    const comments = snapshot.docs
      .map(doc => ({ ...doc.data() }))
      .sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
    res.json({ comments });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

/**
 * POST /api/social/posts/:id/comments — Add a comment
 * Body: { content }
 */
router.post('/posts/:id/comments', requireAuth, async (req, res) => {
  try {
    const { content } = req.body;
    if (!content || !content.trim()) {
      return res.status(400).json({ error: 'Comment content is required' });
    }

    const userDoc = await db().collection('users').doc(req.user.uid).get();
    const userProfile = userDoc.exists ? userDoc.data() : {};

    const commentRef = db().collection('comments').doc();
    const comment = {
      commentID: commentRef.id,
      postID: req.params.id,
      userID: req.user.uid,
      username: userProfile.username || '',
      userProfileImageUrl: userProfile.profileImageUrl || '',
      content: content.trim(),
      createdAt: Date.now()
    };

    // Atomic: create comment + increment counter
    await db().runTransaction(async (t) => {
      const postRef = db().collection('posts').doc(req.params.id);
      const postDoc = await t.get(postRef);
      if (!postDoc.exists) throw new Error('Post does not exist');

      t.set(commentRef, comment);
      t.update(postRef, { numComments: admin.firestore.FieldValue.increment(1) });
    });

    res.status(201).json(comment);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// ============ FOLLOW ============

/**
 * POST /api/social/follow/:uid — Toggle follow
 */
router.post('/follow/:uid', requireAuth, async (req, res) => {
  try {
    const targetUID = req.params.uid;
    if (targetUID === req.user.uid) {
      return res.status(400).json({ error: 'Cannot follow yourself' });
    }

    const followID = `${req.user.uid}_${targetUID}`;
    const followRef = db().collection('follows').doc(followID);
    const followDoc = await followRef.get();

    if (followDoc.exists) {
      // Unfollow
      await followRef.delete();
      await db().collection('users').doc(req.user.uid)
        .update({ numFollowing: admin.firestore.FieldValue.increment(-1) });
      await db().collection('users').doc(targetUID)
        .update({ numFollowers: admin.firestore.FieldValue.increment(-1) });
      res.json({ following: false });
    } else {
      // Follow
      await followRef.set({
        followID,
        followerID: req.user.uid,
        followingID: targetUID,
        createdAt: Date.now()
      });
      await db().collection('users').doc(req.user.uid)
        .update({ numFollowing: admin.firestore.FieldValue.increment(1) });
      await db().collection('users').doc(targetUID)
        .update({ numFollowers: admin.firestore.FieldValue.increment(1) });
      res.json({ following: true });
    }
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

/**
 * GET /api/social/follow/status/:uid — Check if following
 */
router.get('/follow/status/:uid', requireAuth, async (req, res) => {
  try {
    const followID = `${req.user.uid}_${req.params.uid}`;
    const doc = await db().collection('follows').doc(followID).get();
    res.json({ following: doc.exists });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// ============ USER SEARCH ============

/**
 * GET /api/social/users/search?q=query
 */
router.get('/users/search', async (req, res) => {
  try {
    const query = (req.query.q || '').toLowerCase().trim();
    if (query.length < 1) {
      return res.status(400).json({ error: 'Query is required' });
    }

    const firstChar = query[0];
    const snapshot = await db().collection('users')
      .orderBy('username')
      .startAt(firstChar)
      .endAt(firstChar + '\uf8ff')
      .limit(50)
      .get();

    const users = snapshot.docs.map(doc => doc.data());

    // Client-side ranking (port of SocialRepository.searchUsers)
    const ranked = users
      .map(user => {
        const usernameScore = scoreMatch(user.username || '', query);
        const displayScore = scoreMatch(user.displayname || '', query);
        return { user, score: usernameScore + displayScore };
      })
      .filter(r => r.score > 0)
      .sort((a, b) => b.score - a.score)
      .slice(0, 20)
      .map(r => r.user);

    res.json({ users: ranked });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

function scoreMatch(text, query) {
  const lText = text.toLowerCase();
  const lQuery = query.toLowerCase();
  if (lText === lQuery) return 1000;
  const index = lText.indexOf(lQuery);
  if (index === -1) return 0;
  return (lQuery.length * 10) + (100 - index);
}

/**
 * GET /api/social/users/:uid — Get a user's profile
 */
router.get('/users/:uid', async (req, res) => {
  try {
    const doc = await db().collection('users').doc(req.params.uid).get();
    if (!doc.exists) return res.status(404).json({ error: 'User not found' });
    res.json({ user: doc.data() });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

/**
 * GET /api/social/users/:uid/posts — Get a user's posts
 */
router.get('/users/:uid/posts', async (req, res) => {
  try {
    const snapshot = await db().collection('posts')
      .where('userID', '==', req.params.uid)
      .limit(20)
      .get();

    const posts = snapshot.docs
      .map(doc => ({ postID: doc.id, ...doc.data() }))
      .sort((a, b) => (b.created || 0) - (a.created || 0));
    res.json({ posts });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

module.exports = router;
