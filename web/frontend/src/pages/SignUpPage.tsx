import { useState } from 'react';
import { useAuth } from '../hooks/useAuth';
import { useNavigate, Link } from 'react-router-dom';
import { authApi } from '../services/api';

export default function SignUpPage() {
  const { signup } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await signup(email, password);
      // Create profile in Firestore
      await authApi.createProfile({ username: username || email.split('@')[0] });
      navigate('/questionnaire');
    } catch (err: any) {
      setError(err.message?.includes('auth/email-already-in-use')
        ? 'An account with this email already exists'
        : err.message || 'Sign up failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card animate-in">
        <div className="auth-logo">
          <div style={{ fontSize: '3rem', marginBottom: 8 }}>🥗</div>
          <h1>NutriScan Cloud</h1>
          <p>Create your account</p>
        </div>

        {error && <div className="auth-error">{error}</div>}

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label">Username</label>
            <input
              id="signup-username"
              className="form-input"
              type="text"
              value={username}
              onChange={e => setUsername(e.target.value)}
              placeholder="nutri_user"
              required
            />
          </div>
          <div className="form-group">
            <label className="form-label">Email</label>
            <input
              id="signup-email"
              className="form-input"
              type="email"
              value={email}
              onChange={e => setEmail(e.target.value)}
              placeholder="you@example.com"
              required
            />
          </div>
          <div className="form-group">
            <label className="form-label">Password</label>
            <input
              id="signup-password"
              className="form-input"
              type="password"
              value={password}
              onChange={e => setPassword(e.target.value)}
              placeholder="Minimum 6 characters"
              required
              minLength={6}
            />
          </div>
          <button
            id="signup-submit"
            className="btn btn-primary btn-lg"
            type="submit"
            disabled={loading}
            style={{ width: '100%' }}
          >
            {loading ? 'Creating account...' : 'Create Account'}
          </button>
        </form>

        <div className="auth-footer">
          Already have an account?{' '}
          <Link to="/login">Sign in</Link>
        </div>
      </div>
    </div>
  );
}
