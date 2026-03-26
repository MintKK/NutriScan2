import { useState, useEffect } from 'react';
import { mealsApi, socialApi } from '../services/api';
import { useAuth } from '../hooks/useAuth';
import { Link } from 'react-router-dom';

interface Meal {
  id: string;
  foodName: string;
  grams: number;
  kcalTotal: number;
  proteinTotal: number;
  carbsTotal: number;
  fatTotal: number;
}

interface Insight {
  emoji: string;
  message: string;
  type: string;
}

export default function DashboardPage() {
  const { user } = useAuth();
  const [meals, setMeals] = useState<Meal[]>([]);
  const [totals, setTotals] = useState({ kcal: 0, protein: 0, carbs: 0, fat: 0 });
  const [insights, setInsights] = useState<Insight[]>([]);
  const [calorieGoal, setCalorieGoal] = useState(2000);
  const [loading, setLoading] = useState(true);

  // Share state
  const [sharingMeal, setSharingMeal] = useState<Meal | null>(null);
  const [shareCaption, setShareCaption] = useState('');
  const [sharing, setSharing] = useState(false);
  const [shareSuccess, setShareSuccess] = useState('');

  useEffect(() => {
    loadDashboard();
  }, []);

  const loadDashboard = async () => {
    try {
      const [todayRes, insRes] = await Promise.all([
        mealsApi.getToday(),
        mealsApi.getInsights()
      ]);
      setMeals(todayRes.data.meals || []);
      setTotals(todayRes.data.totals || { kcal: 0, protein: 0, carbs: 0, fat: 0 });
      setInsights(insRes.data.insights || []);
      setCalorieGoal(insRes.data.calorieGoal || 2000);
    } catch (err) {
      console.error('Dashboard load error:', err);
    } finally {
      setLoading(false);
    }
  };

  const deleteMeal = async (id: string) => {
    try {
      await mealsApi.deleteMeal(id);
      loadDashboard();
    } catch (err) {
      console.error('Delete error:', err);
    }
  };

  const handleShareMeal = async () => {
    if (!sharingMeal || sharing) return;
    setSharing(true);
    try {
      await socialApi.createPost({
        caption: shareCaption,
        foodName: sharingMeal.foodName,
        calories: sharingMeal.kcalTotal,
        protein: Math.round(sharingMeal.proteinTotal),
        carbs: Math.round(sharingMeal.carbsTotal),
        fat: Math.round(sharingMeal.fatTotal),
        foodImageUrl: ''
      });
      setSharingMeal(null);
      setShareCaption('');
      setShareSuccess('Shared to community! 🎉');
      setTimeout(() => setShareSuccess(''), 3000);
    } catch (err) {
      console.error('Share error:', err);
    } finally {
      setSharing(false);
    }
  };

  const caloriePercent = Math.min(100, Math.round((totals.kcal / calorieGoal) * 100));
  const circumference = 2 * Math.PI * 72;
  const strokeDashoffset = circumference - (caloriePercent / 100) * circumference;

  // Determine ring color
  const ringColor = caloriePercent > 100 ? '#ef4444' : caloriePercent > 85 ? '#f59e0b' : '#10b981';

  if (loading) return <div className="spinner" />;

  return (
    <div className="slide-up">
      <div className="page-header">
        <h1 className="page-title">Dashboard</h1>
        <Link to="/add-meal" className="btn btn-primary">
          ＋ Add Meal
        </Link>
      </div>

      {/* Share Success Toast */}
      {shareSuccess && (
        <div className="coach-tip animate-in" style={{ marginBottom: 16 }}>
          <span className="tip-icon">✅</span>
          <span>{shareSuccess}</span>
        </div>
      )}

      {/* Share Modal */}
      {sharingMeal && (
        <div className="share-overlay" onClick={() => setSharingMeal(null)}>
          <div className="share-modal animate-in" onClick={e => e.stopPropagation()}>
            <div className="card-header">
              <span className="card-title">📤 Share to Community</span>
              <button className="btn btn-ghost btn-sm" onClick={() => setSharingMeal(null)}>✕</button>
            </div>

            <div style={{
              background: 'var(--bg-elevated)', borderRadius: 'var(--radius-md)',
              padding: 16, marginBottom: 16
            }}>
              <div style={{ fontWeight: 600, marginBottom: 4 }}>{sharingMeal.foodName}</div>
              <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                {sharingMeal.kcalTotal} kcal · P:{Math.round(sharingMeal.proteinTotal)}g · C:{Math.round(sharingMeal.carbsTotal)}g · F:{Math.round(sharingMeal.fatTotal)}g
              </div>
            </div>

            <div className="form-group">
              <label className="form-label">Add a caption (optional)</label>
              <textarea
                className="form-textarea"
                placeholder="What do you want to say about this meal?"
                value={shareCaption}
                onChange={e => setShareCaption(e.target.value)}
                rows={2}
              />
            </div>

            <div style={{ display: 'flex', gap: 12 }}>
              <button className="btn btn-secondary" onClick={() => setSharingMeal(null)}>Cancel</button>
              <button
                className="btn btn-primary"
                style={{ flex: 1 }}
                onClick={handleShareMeal}
                disabled={sharing}
              >
                {sharing ? 'Sharing...' : '📤 Share'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Hero: Calorie Ring + Macros */}
      <div className="card" style={{ marginBottom: 24 }}>
        <div className="dashboard-hero">
          <div className="calorie-ring-container">
            <svg width="180" height="180" viewBox="0 0 180 180">
              <circle cx="90" cy="90" r="72" fill="none" stroke="#334155" strokeWidth="12" />
              <circle
                cx="90" cy="90" r="72" fill="none"
                stroke={ringColor}
                strokeWidth="12"
                strokeLinecap="round"
                strokeDasharray={circumference}
                strokeDashoffset={strokeDashoffset}
                transform="rotate(-90 90 90)"
                style={{ transition: 'stroke-dashoffset 0.8s ease, stroke 0.3s' }}
              />
            </svg>
            <div className="calorie-ring-text">
              <div className="value" style={{ color: ringColor }}>{totals.kcal}</div>
              <div className="label">of {calorieGoal} kcal</div>
            </div>
          </div>

          <div className="hero-stats">
            <div className="hero-greeting">
              {new Date().getHours() < 12 ? '🌅 Good Morning' :
               new Date().getHours() < 17 ? '☀️ Good Afternoon' : '🌙 Good Evening'},
              {' '}{user?.displayName || user?.email?.split('@')[0] || 'there'}!
            </div>

            <div className="macro-bars">
              {[
                { name: 'Protein', key: 'protein', color: 'protein', goal: Math.round(calorieGoal * 0.25 / 4), unit: 'g' },
                { name: 'Carbs', key: 'carbs', color: 'carbs', goal: Math.round(calorieGoal * 0.50 / 4), unit: 'g' },
                { name: 'Fat', key: 'fat', color: 'fat', goal: Math.round(calorieGoal * 0.25 / 9), unit: 'g' },
              ].map(macro => {
                const val = totals[macro.key as keyof typeof totals];
                const pct = Math.min(100, Math.round((val / macro.goal) * 100));
                return (
                  <div className="macro-bar" key={macro.key}>
                    <div className="macro-bar-header">
                      <span className="macro-bar-name">{macro.name}</span>
                      <span className="macro-bar-value">{Math.round(val)}/{macro.goal}{macro.unit}</span>
                    </div>
                    <div className="macro-bar-track">
                      <div className={`macro-bar-fill ${macro.color}`} style={{ width: `${pct}%` }} />
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </div>

      <div className="dashboard-grid">
        {/* AI Coach Insights */}
        <div className="card">
          <div className="card-header">
            <span className="card-title">🧠 AI Coach</span>
          </div>
          {insights.length > 0 ? (
            insights.map((i, idx) => (
              <div className="insight-card" key={idx}>
                <span className="insight-emoji">{i.emoji}</span>
                <span className="insight-message">{i.message}</span>
              </div>
            ))
          ) : (
            <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>
              Log a meal to see personalized insights!
            </p>
          )}
        </div>

        {/* Today's Meals */}
        <div className="card">
          <div className="card-header">
            <span className="card-title">🍽️ Today's Meals</span>
            <span style={{ color: 'var(--text-muted)', fontSize: '0.8rem' }}>
              {meals.length} meal{meals.length !== 1 ? 's' : ''}
            </span>
          </div>
          {meals.length > 0 ? (
            meals.map(meal => (
              <div className="meal-item" key={meal.id}>
                <div className="meal-icon">🍲</div>
                <div className="meal-details">
                  <div className="meal-name">{meal.foodName}</div>
                  <div className="meal-meta">{meal.grams}g • P:{Math.round(meal.proteinTotal)}g C:{Math.round(meal.carbsTotal)}g F:{Math.round(meal.fatTotal)}g</div>
                </div>
                <div className="meal-kcal">{meal.kcalTotal} kcal</div>
                <button className="btn btn-ghost btn-sm" onClick={() => setSharingMeal(meal)} title="Share to Community">📤</button>
                <button className="btn btn-ghost btn-sm" onClick={() => deleteMeal(meal.id)} title="Delete">🗑️</button>
              </div>
            ))
          ) : (
            <div className="empty-state">
              <div className="emoji">📷</div>
              <div className="message">No meals logged today</div>
              <Link to="/add-meal" className="btn btn-primary btn-sm">Add your first meal</Link>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
