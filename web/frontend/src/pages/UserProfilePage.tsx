import { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { socialApi } from '../services/api';
import { useAuth } from '../hooks/useAuth';

interface UserProfile {
  uid: string;
  username: string;
  displayname: string;
  bio: string;
  profileImageUrl: string;
  numFollowers: number;
  numFollowing: number;
  numPosts: number;
}

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
}

export default function UserProfilePage() {
  const { uid } = useParams<{ uid: string }>();
  const { user } = useAuth();
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [posts, setPosts] = useState<Post[]>([]);
  const [following, setFollowing] = useState(false);
  const [loading, setLoading] = useState(true);
  const [toggling, setToggling] = useState(false);

  const isOwnProfile = user?.uid === uid;

  useEffect(() => {
    if (uid) loadProfile();
  }, [uid]);

  const loadProfile = async () => {
    try {
      const [profileRes, postsRes] = await Promise.all([
        socialApi.getUserProfile(uid!),
        socialApi.getUserPosts(uid!),
      ]);
      setProfile(profileRes.data.user);
      setPosts(postsRes.data.posts || []);

      if (!isOwnProfile) {
        const followRes = await socialApi.getFollowStatus(uid!);
        setFollowing(followRes.data.following);
      }
    } catch (err) {
      console.error('Profile load error:', err);
    } finally {
      setLoading(false);
    }
  };

  const toggleFollow = async () => {
    if (!uid || toggling) return;
    setToggling(true);
    try {
      const res = await socialApi.toggleFollow(uid);
      setFollowing(res.data.following);
      setProfile(prev => prev ? {
        ...prev,
        numFollowers: prev.numFollowers + (res.data.following ? 1 : -1)
      } : null);
    } catch (err) {
      console.error('Follow error:', err);
    } finally {
      setToggling(false);
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

  if (!profile) {
    return (
      <div className="empty-state">
        <div className="emoji">👤</div>
        <div className="message">User not found</div>
        <Link to="/social" className="btn btn-primary btn-sm">Back to Community</Link>
      </div>
    );
  }

  return (
    <div className="slide-up" style={{ maxWidth: 640, margin: '0 auto' }}>
      {/* Profile Header */}
      <div className="card profile-card" style={{ marginBottom: 24 }}>
        <div className="profile-header">
          <div className="profile-avatar-lg">
            {(profile.username || '?')[0].toUpperCase()}
          </div>
          <div className="profile-info">
            <h2 className="profile-name">{profile.displayname || profile.username}</h2>
            <p className="profile-username">@{profile.username}</p>
            {profile.bio && <p className="profile-bio">{profile.bio}</p>}
          </div>
        </div>

        {/* Stats */}
        <div className="profile-stats">
          <div className="profile-stat">
            <div className="profile-stat-value">{profile.numPosts}</div>
            <div className="profile-stat-label">Posts</div>
          </div>
          <div className="profile-stat">
            <div className="profile-stat-value">{profile.numFollowers}</div>
            <div className="profile-stat-label">Followers</div>
          </div>
          <div className="profile-stat">
            <div className="profile-stat-value">{profile.numFollowing}</div>
            <div className="profile-stat-label">Following</div>
          </div>
        </div>

        {/* Follow Button */}
        {!isOwnProfile && (
          <button
            className={`btn ${following ? 'btn-secondary' : 'btn-primary'}`}
            style={{ width: '100%', marginTop: 16 }}
            onClick={toggleFollow}
            disabled={toggling}
          >
            {toggling ? '...' : following ? 'Following ✓' : 'Follow'}
          </button>
        )}
      </div>

      {/* User's Posts */}
      <div className="card-header" style={{ marginBottom: 16 }}>
        <span className="card-title">📝 Posts</span>
        <span style={{ color: 'var(--text-muted)', fontSize: '0.8rem' }}>
          {posts.length} post{posts.length !== 1 ? 's' : ''}
        </span>
      </div>

      {posts.length === 0 ? (
        <div className="empty-state">
          <div className="emoji">📭</div>
          <div className="message">No posts yet</div>
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
                  {post.numCarbs > 0 && ` · C:${post.numCarbs}g`}
                  {post.numFat > 0 && ` · F:${post.numFat}g`}
                </div>
              )}
            </div>

            <div className="post-actions">
              <span className="post-action-btn">❤️ {post.numLikes}</span>
              <span className="post-action-btn">💬 {post.numComments}</span>
            </div>
          </div>
        ))
      )}
    </div>
  );
}
