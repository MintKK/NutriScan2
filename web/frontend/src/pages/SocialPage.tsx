import { useState, useEffect, useRef } from 'react';
import { socialApi, uploadApi, classifyApi } from '../services/api';
import { useAuth } from '../hooks/useAuth';
import { useFoodClassifier } from '../hooks/useFoodClassifier';
import { Link } from 'react-router-dom';

interface Post {
  postID: string;
  userID: string;
  username: string;
  caption: string;
  foodName: string;
  numCalories: number;
  numProtein: number;
  numCarbs: number;
  numFat: number;
  numLikes: number;
  numComments: number;
  foodImageUrl: string;
  created: number;
  isLiked?: boolean;
}

interface SearchUser {
  uid: string;
  username: string;
  displayname: string;
  profileImageUrl: string;
  numFollowers: number;
  numPosts: number;
}

export default function SocialPage() {
  const { user } = useAuth();
  const { isClassifying, classifyImage } = useFoodClassifier();
  const [posts, setPosts] = useState<Post[]>([]);
  const [loading, setLoading] = useState(true);
  const [commentText, setCommentText] = useState<Record<string, string>>({});
  const [comments, setComments] = useState<Record<string, any[]>>({});
  const [expandedComments, setExpandedComments] = useState<Set<string>>(new Set());

  // Create Post state
  const [showCreatePost, setShowCreatePost] = useState(false);
  const [newPost, setNewPost] = useState({
    caption: '', foodName: '', calories: 0, protein: 0, carbs: 0, fat: 0, foodImageUrl: ''
  });
  const [grams, setGrams] = useState(100);
  const [per100g, setPer100g] = useState<{ kcal: number; protein: number; carbs: number; fat: number } | null>(null);
  const [posting, setPosting] = useState(false);
  const [imageFile, setImageFile] = useState<File | null>(null);
  const [imagePreview, setImagePreview] = useState('');
  const [uploading, setUploading] = useState(false);
  const [classifying, setClassifying] = useState(false);
  const [classifyMessage, setClassifyMessage] = useState('');
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [imageUrl, setImageUrl] = useState('');

  // Sort state
  const [sortBy, setSortBy] = useState<'trendingScore' | 'created'>('trendingScore');

  // User Search state
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<SearchUser[]>([]);
  const [searching, setSearching] = useState(false);
  const [showSearch, setShowSearch] = useState(false);

  // Delete state
  const [deleting, setDeleting] = useState<string | null>(null);

  useEffect(() => { loadFeed(); }, [sortBy]);

  const loadFeed = async () => {
    try {
      const res = await socialApi.getFeed(sortBy);
      setPosts(res.data.posts || []);
    } catch (err) {
      console.error('Feed error:', err);
    } finally {
      setLoading(false);
    }
  };

  // Shared helper to process classification results
  const applyClassificationResult = (result: any) => {
    const candidates = result.candidates || [];
    if (candidates.length > 0) {
      const top = candidates[0];
      const kcalPer100 = Math.round(top.food.kcalPer100g);
      const protPer100 = Math.round(top.food.proteinPer100g);
      const carbsPer100 = Math.round(top.food.carbsPer100g);
      const fatPer100 = Math.round(top.food.fatPer100g);
      setPer100g({ kcal: kcalPer100, protein: protPer100, carbs: carbsPer100, fat: fatPer100 });
      setGrams(100);
      setNewPost(prev => ({
        ...prev,
        foodName: top.food.name,
        calories: kcalPer100,
        protein: protPer100,
        carbs: carbsPer100,
        fat: fatPer100,
      }));
      setClassifyMessage(`Detected: ${top.food.name} (${top.confidence}% confidence) — adjust weight below`);
    } else {
      setClassifyMessage(result.message || 'No food match found. Fill in details manually.');
    }
  };

  const handleImageSelect = async (file: File) => {
    if (!file.type.startsWith('image/')) return;
    setImageFile(file);
    setImageUrl('');
    setImagePreview(URL.createObjectURL(file));
    setClassifyMessage('');

    setClassifying(true);
    setClassifyMessage('Analyzing food...');
    try {
      // Send image to backend → inference service → food matching pipeline
      const result = await classifyImage(file);
      applyClassificationResult(result);
    } catch (err) {
      console.error('Classification error:', err);
      setClassifyMessage('Classification failed. Fill in details manually.');
    } finally {
      setClassifying(false);
    }
  };

  const handleImageUrlSelect = async () => {
    const url = imageUrl.trim();
    if (!url) return;

    setImageFile(null);
    setImagePreview(url);
    setNewPost(prev => ({ ...prev, foodImageUrl: url }));
    setClassifyMessage('');

    setClassifying(true);
    setClassifyMessage('Analyzing food...');
    try {
      // Fetch the image via our proxy, then send as file to classify endpoint
      const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:3001';
      const proxyUrl = `${API_BASE}/api/proxy-image?url=${encodeURIComponent(url)}`;
      const response = await fetch(proxyUrl);
      const blob = await response.blob();
      const file = new File([blob], 'url-image.jpg', { type: blob.type || 'image/jpeg' });

      const result = await classifyImage(file);
      applyClassificationResult(result);
    } catch (err) {
      console.error('URL classification error:', err);
      setClassifyMessage('Could not load image from URL. Check the link or try uploading instead.');
    } finally {
      setClassifying(false);
    }
  };

  const handleCreatePost = async () => {
    if (!newPost.foodName.trim()) return;
    setPosting(true);
    try {
      let foodImageUrl = newPost.foodImageUrl;

      // Upload image file if selected (skip if user provided a URL directly)
      if (imageFile) {
        setUploading(true);
        const uploadRes = await uploadApi.uploadImage(imageFile);
        foodImageUrl = uploadRes.data.imageUrl;
        setUploading(false);
      } else if (imageUrl.trim()) {
        foodImageUrl = imageUrl.trim();
      }

      await socialApi.createPost({
        caption: newPost.caption,
        foodName: newPost.foodName,
        calories: newPost.calories,
        protein: newPost.protein,
        carbs: newPost.carbs,
        fat: newPost.fat,
        foodImageUrl
      });
      setNewPost({ caption: '', foodName: '', calories: 0, protein: 0, carbs: 0, fat: 0, foodImageUrl: '' });
      setImageFile(null);
      setImagePreview('');
      setImageUrl('');
      setShowCreatePost(false);
      loadFeed();
    } catch (err) {
      console.error('Create post error:', err);
    } finally {
      setPosting(false);
      setUploading(false);
    }
  };

  const handleDeletePost = async (postId: string) => {
    if (deleting) return;
    setDeleting(postId);
    try {
      await socialApi.deletePost(postId);
      setPosts(prev => prev.filter(p => p.postID !== postId));
    } catch (err) {
      console.error('Delete error:', err);
    } finally {
      setDeleting(null);
    }
  };

  const handleSearch = async () => {
    if (searchQuery.length < 1) return;
    setSearching(true);
    try {
      const res = await socialApi.searchUsers(searchQuery);
      setSearchResults(res.data.users || []);
    } catch (err) {
      console.error('Search error:', err);
    } finally {
      setSearching(false);
    }
  };

  const toggleLike = async (postId: string) => {
    try {
      const res = await socialApi.toggleLike(postId);
      setPosts(prev => prev.map(p =>
        p.postID === postId ? {
          ...p,
          isLiked: res.data.liked,
          numLikes: p.numLikes + (res.data.liked ? 1 : -1)
        } : p
      ));
    } catch (err) {
      console.error('Like error:', err);
    }
  };

  const loadComments = async (postId: string) => {
    if (expandedComments.has(postId)) {
      setExpandedComments(prev => { const s = new Set(prev); s.delete(postId); return s; });
      return;
    }
    try {
      const res = await socialApi.getComments(postId);
      setComments(prev => ({ ...prev, [postId]: res.data.comments || [] }));
      setExpandedComments(prev => new Set(prev).add(postId));
    } catch (err) {
      console.error('Comments error:', err);
    }
  };

  const addComment = async (postId: string) => {
    const text = commentText[postId]?.trim();
    if (!text) return;
    try {
      const res = await socialApi.addComment(postId, text);
      setComments(prev => ({
        ...prev,
        [postId]: [res.data, ...(prev[postId] || [])]
      }));
      setCommentText(prev => ({ ...prev, [postId]: '' }));
      setPosts(prev => prev.map(p =>
        p.postID === postId ? { ...p, numComments: p.numComments + 1 } : p
      ));
    } catch (err) {
      console.error('Comment error:', err);
    }
  };

  const timeAgo = (ms: number) => {
    const diff = Date.now() - ms;
    const mins = Math.floor(diff / 60000);
    if (mins < 60) return `${mins}m ago`;
    const hrs = Math.floor(mins / 60);
    if (hrs < 24) return `${hrs}h ago`;
    return `${Math.floor(hrs / 24)}d ago`;
  };

  if (loading) return <div className="spinner" />;

  return (
    <div className="slide-up" style={{ maxWidth: 640, margin: '0 auto' }}>
      <div className="page-header">
        <h1 className="page-title">Community</h1>
        <div style={{ display: 'flex', gap: 8 }}>
          <button
            className="btn btn-ghost btn-sm"
            onClick={() => { setShowSearch(!showSearch); setShowCreatePost(false); }}
          >
            🔍 Search
          </button>
          <button
            className="btn btn-primary btn-sm"
            onClick={() => { setShowCreatePost(!showCreatePost); setShowSearch(false); }}
          >
            ＋ Share Meal
          </button>
        </div>
      </div>

      {/* User Search */}
      {showSearch && (
        <div className="card animate-in" style={{ marginBottom: 20 }}>
          <div className="card-header">
            <span className="card-title">🔍 Find Users</span>
            <button className="btn btn-ghost btn-sm" onClick={() => setShowSearch(false)}>✕</button>
          </div>
          <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
            <div className="search-bar" style={{ flex: 1 }}>
              <span className="search-icon">👤</span>
              <input
                id="user-search"
                placeholder="Search by username..."
                value={searchQuery}
                onChange={e => setSearchQuery(e.target.value)}
                onKeyDown={e => e.key === 'Enter' && handleSearch()}
              />
            </div>
            <button className="btn btn-primary btn-sm" onClick={handleSearch} disabled={searching}>
              {searching ? '...' : 'Search'}
            </button>
          </div>
          {searchResults.length > 0 && (
            <div className="user-search-results">
              {searchResults.map(u => (
                <Link to={`/profile/${u.uid}`} key={u.uid} className="user-result-item">
                  <div className="post-avatar" style={{ width: 36, height: 36, fontSize: '0.8rem' }}>
                    {(u.username || '?')[0].toUpperCase()}
                  </div>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontWeight: 600, fontSize: '0.9rem' }}>
                      {u.displayname || u.username}
                    </div>
                    <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                      @{u.username} · {u.numPosts} posts · {u.numFollowers} followers
                    </div>
                  </div>
                  <span style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>→</span>
                </Link>
              ))}
            </div>
          )}
          {searchResults.length === 0 && searchQuery && !searching && (
            <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', textAlign: 'center', padding: 12 }}>
              No users found
            </p>
          )}
        </div>
      )}

      {/* Create Post Form */}
      {showCreatePost && (
        <div className="card animate-in" style={{ marginBottom: 20 }}>
          <div className="card-header">
            <span className="card-title">📝 Share a Meal</span>
            <button className="btn btn-ghost btn-sm" onClick={() => setShowCreatePost(false)}>✕</button>
          </div>

          {/* Image Upload — placed first to enable auto-classification */}
          <div className="form-group">
            <label className="form-label">Food Photo (uploads & auto-detects food)</label>
            {imagePreview ? (
              <div style={{ position: 'relative', marginBottom: 8 }}>
                <img src={imagePreview} alt="Preview"
                  style={{ width: '100%', maxHeight: 200, objectFit: 'cover', borderRadius: 'var(--radius-md)', opacity: classifying ? 0.5 : 1, transition: 'opacity 0.3s' }} />
                {classifying && (
                  <div style={{ position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%, -50%)' }}>
                    <div className="spinner" style={{ margin: 0 }} />
                  </div>
                )}
                <button
                  className="btn btn-ghost btn-sm"
                  style={{ position: 'absolute', top: 8, right: 8, background: 'rgba(0,0,0,0.6)' }}
                  onClick={() => { setImageFile(null); setImagePreview(''); setImageUrl(''); setClassifyMessage(''); setNewPost(prev => ({ ...prev, foodImageUrl: '' })); }}
                >✕</button>
              </div>
            ) : (
              <div
                className="upload-zone"
                style={{ padding: 24, cursor: 'pointer' }}
                onClick={() => fileInputRef.current?.click()}
              >
                <div style={{ fontSize: '1.5rem', marginBottom: 4 }}>📷</div>
                <div style={{ fontSize: '0.9rem', fontWeight: 600 }}>Click to upload a photo</div>
                <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>AI will auto-detect food & macros · JPEG, PNG, WebP</div>
              </div>
            )}
            <input
              ref={fileInputRef}
              type="file"
              accept="image/jpeg,image/png,image/webp"
              style={{ display: 'none' }}
              onChange={e => e.target.files?.[0] && handleImageSelect(e.target.files[0])}
            />

            {/* URL input for image */}
            <div style={{ display: 'flex', gap: 8, marginTop: 12, alignItems: 'center' }}>
              <span style={{ fontSize: '1.2rem' }}>🔗</span>
              <input
                id="social-image-url-input"
                className="form-input"
                type="url"
                placeholder="Or paste an image URL..."
                value={imageUrl}
                onChange={e => setImageUrl(e.target.value)}
                onKeyDown={e => e.key === 'Enter' && handleImageUrlSelect()}
                style={{ flex: 1 }}
              />
              <button
                className="btn btn-primary btn-sm"
                onClick={handleImageUrlSelect}
                disabled={classifying || !imageUrl.trim()}
              >
                {classifying ? 'Analyzing...' : 'Classify'}
              </button>
            </div>
          </div>

          {/* Classification result message */}
          {classifyMessage && (
            <div className="coach-tip" style={{ marginBottom: 16 }}>
              <span className="tip-icon">{classifying ? '🔄' : classifyMessage.includes('Detected') ? '✅' : '💡'}</span>
              <span>{classifyMessage}</span>
            </div>
          )}

          <div className="form-group">
            <label className="form-label">Food Name *</label>
            <input
              id="post-food-name"
              className="form-input"
              placeholder="e.g. Grilled Chicken Salad"
              value={newPost.foodName}
              onChange={e => setNewPost({ ...newPost, foodName: e.target.value })}
            />
          </div>

          <div className="form-group">
            <label className="form-label">Caption</label>
            <textarea
              id="post-caption"
              className="form-textarea"
              placeholder="What's on your mind about this meal?"
              value={newPost.caption}
              onChange={e => setNewPost({ ...newPost, caption: e.target.value })}
              rows={2}
            />
          </div>

          <div className="form-group">
            <label className="form-label">Weight (g)</label>
            <input className="form-input" type="number" placeholder="100"
              value={grams || ''}
              onChange={e => {
                const g = parseInt(e.target.value) || 0;
                setGrams(g);
                if (per100g && g > 0) {
                  setNewPost(prev => ({
                    ...prev,
                    calories: Math.round(per100g.kcal * g / 100),
                    protein: Math.round(per100g.protein * g / 100),
                    carbs: Math.round(per100g.carbs * g / 100),
                    fat: Math.round(per100g.fat * g / 100),
                  }));
                }
              }} />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 8, marginBottom: 16 }}>
            <div className="form-group" style={{ marginBottom: 0 }}>
              <label className="form-label">Calories</label>
              <input className="form-input" type="number" placeholder="0"
                value={newPost.calories || ''}
                onChange={e => setNewPost({ ...newPost, calories: parseInt(e.target.value) || 0 })} />
            </div>
            <div className="form-group" style={{ marginBottom: 0 }}>
              <label className="form-label">Protein (g)</label>
              <input className="form-input" type="number" placeholder="0"
                value={newPost.protein || ''}
                onChange={e => setNewPost({ ...newPost, protein: parseInt(e.target.value) || 0 })} />
            </div>
            <div className="form-group" style={{ marginBottom: 0 }}>
              <label className="form-label">Carbs (g)</label>
              <input className="form-input" type="number" placeholder="0"
                value={newPost.carbs || ''}
                onChange={e => setNewPost({ ...newPost, carbs: parseInt(e.target.value) || 0 })} />
            </div>
            <div className="form-group" style={{ marginBottom: 0 }}>
              <label className="form-label">Fat (g)</label>
              <input className="form-input" type="number" placeholder="0"
                value={newPost.fat || ''}
                onChange={e => setNewPost({ ...newPost, fat: parseInt(e.target.value) || 0 })} />
            </div>
          </div>

          <button
            id="post-submit"
            className="btn btn-primary"
            style={{ width: '100%' }}
            onClick={handleCreatePost}
            disabled={posting || uploading || classifying || !newPost.foodName.trim()}
          >
            {uploading ? 'Uploading image...' : posting ? 'Posting...' : '📤 Share to Community'}
          </button>
        </div>
      )}

      {/* Sort Toggle */}
      <div className="tabs" style={{ marginBottom: 20 }}>
        <button
          className={`tab ${sortBy === 'trendingScore' ? 'active' : ''}`}
          onClick={() => setSortBy('trendingScore')}
        >
          🔥 Trending
        </button>
        <button
          className={`tab ${sortBy === 'created' ? 'active' : ''}`}
          onClick={() => setSortBy('created')}
        >
          🕐 Latest
        </button>
      </div>

      {/* Feed */}
      {posts.length === 0 ? (
        <div className="empty-state">
          <div className="emoji">🌍</div>
          <div className="message">No posts yet. Be the first to share!</div>
          <button className="btn btn-primary btn-sm" onClick={() => setShowCreatePost(true)}>
            Share a Meal
          </button>
        </div>
      ) : (
        posts.map(post => (
          <div className="post-card" key={post.postID}>
            <div className="post-header">
              <Link to={`/profile/${post.userID}`} className="post-avatar" style={{ textDecoration: 'none', color: 'inherit' }}>
                {(post.username || '?')[0].toUpperCase()}
              </Link>
              <div className="post-user-info" style={{ flex: 1 }}>
                <Link to={`/profile/${post.userID}`} style={{ textDecoration: 'none', color: 'inherit' }}>
                  <div className="post-username">{post.username || 'User'}</div>
                </Link>
                <div className="post-time">{timeAgo(post.created)}</div>
              </div>
              {/* Delete button for own posts */}
              {user?.uid === post.userID && (
                <button
                  className="btn btn-ghost btn-sm"
                  onClick={() => handleDeletePost(post.postID)}
                  disabled={deleting === post.postID}
                  title="Delete post"
                  style={{ padding: '4px 8px' }}
                >
                  {deleting === post.postID ? '...' : '🗑️'}
                </button>
              )}
            </div>

            {post.foodImageUrl && (
              <img className="post-image" src={post.foodImageUrl} alt={post.foodName} />
            )}

            <div className="post-body">
              {post.caption && <p className="post-caption">{post.caption}</p>}
              {post.foodName && (
                <div className="post-food-tag">
                  🍽️ {post.foodName} · {post.numCalories} kcal
                  {post.numProtein > 0 && ` · P:${post.numProtein}g`}
                  {post.numCarbs > 0 && ` · C:${post.numCarbs}g`}
                  {post.numFat > 0 && ` · F:${post.numFat}g`}
                </div>
              )}
            </div>

            <div className="post-actions">
              <button
                className={`post-action-btn ${post.isLiked ? 'liked' : ''}`}
                onClick={() => toggleLike(post.postID)}
              >
                {post.isLiked ? '❤️' : '🤍'} {post.numLikes}
              </button>
              <button
                className="post-action-btn"
                onClick={() => loadComments(post.postID)}
              >
                💬 {post.numComments}
              </button>
            </div>

            {expandedComments.has(post.postID) && (
              <div style={{ padding: '0 16px 16px' }}>
                <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
                  <input
                    className="form-input"
                    placeholder="Write a comment..."
                    value={commentText[post.postID] || ''}
                    onChange={e => setCommentText(prev => ({ ...prev, [post.postID]: e.target.value }))}
                    onKeyDown={e => e.key === 'Enter' && addComment(post.postID)}
                    style={{ flex: 1 }}
                  />
                  <button
                    className="btn btn-primary btn-sm"
                    onClick={() => addComment(post.postID)}
                  >
                    Post
                  </button>
                </div>
                {(comments[post.postID] || []).map((c: any, i: number) => (
                  <div key={i} style={{
                    display: 'flex', gap: 8, marginBottom: 8, padding: '8px 0',
                    borderBottom: '1px solid var(--border)'
                  }}>
                    <div className="post-avatar" style={{ width: 28, height: 28, fontSize: '0.7rem' }}>
                      {(c.username || '?')[0].toUpperCase()}
                    </div>
                    <div>
                      <span style={{ fontWeight: 600, fontSize: '0.85rem' }}>{c.username}</span>
                      <span style={{ color: 'var(--text-secondary)', fontSize: '0.85rem', marginLeft: 8 }}>{c.content}</span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        ))
      )}
    </div>
  );
}
