import { useState, useEffect } from 'react';
import { socialApi } from '../services/api';
import { useAuth } from '../hooks/useAuth';

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

export default function SocialPage() {
  const { user } = useAuth();
  const [posts, setPosts] = useState<Post[]>([]);
  const [loading, setLoading] = useState(true);
  const [commentText, setCommentText] = useState<Record<string, string>>({});
  const [comments, setComments] = useState<Record<string, any[]>>({});
  const [expandedComments, setExpandedComments] = useState<Set<string>>(new Set());

  useEffect(() => { loadFeed(); }, []);

  const loadFeed = async () => {
    try {
      const res = await socialApi.getFeed();
      setPosts(res.data.posts || []);
    } catch (err) {
      console.error('Feed error:', err);
    } finally {
      setLoading(false);
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
      </div>

      {posts.length === 0 ? (
        <div className="empty-state">
          <div className="emoji">🌍</div>
          <div className="message">No posts yet. Be the first to share!</div>
        </div>
      ) : (
        posts.map(post => (
          <div className="post-card" key={post.postID}>
            <div className="post-header">
              <div className="post-avatar">
                {(post.username || '?')[0].toUpperCase()}
              </div>
              <div className="post-user-info">
                <div className="post-username">{post.username || 'User'}</div>
                <div className="post-time">{timeAgo(post.created)}</div>
              </div>
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
