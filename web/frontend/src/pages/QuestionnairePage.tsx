import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../services/api';

export default function QuestionnairePage() {
  const navigate = useNavigate();
  const [gender, setGender] = useState('');
  const [age, setAge] = useState('');
  const [heightCm, setHeightCm] = useState('');
  const [weightKg, setWeightKg] = useState('');
  const [activityLevel, setActivityLevel] = useState('');
  const [goal, setGoal] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!gender || !age || !heightCm || !weightKg || !activityLevel || !goal) {
      setError('Please fill in all fields');
      return;
    }
    setLoading(true);
    setError('');
    try {
      const res = await authApi.saveQuestionnaire({
        gender, age: parseInt(age), heightCm: parseFloat(heightCm),
        weightKg: parseFloat(weightKg), activityLevel, goal
      });
      navigate('/');
    } catch (err: any) {
      setError(err.message || 'Failed to save');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="questionnaire-page animate-in">
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <div style={{ fontSize: '3rem' }}>🎯</div>
          <h1 style={{ fontSize: '1.75rem', fontWeight: 800, marginTop: 8 }}>Set Your Goals</h1>
          <p style={{ color: 'var(--text-muted)' }}>We'll calculate your personalized nutrition targets</p>
        </div>

        {error && <div className="auth-error">{error}</div>}

        <form onSubmit={handleSubmit}>
          {/* Gender */}
          <div className="questionnaire-step">
            <label className="form-label">Gender</label>
            <div className="radio-group">
              {[['MALE', '♂️ Male'], ['FEMALE', '♀️ Female']].map(([val, label]) => (
                <div key={val} className={`radio-option ${gender === val ? 'selected' : ''}`}
                     onClick={() => setGender(val)}>{label}</div>
              ))}
            </div>
          </div>

          {/* Age, Height, Weight */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12 }}>
            <div className="form-group">
              <label className="form-label">Age</label>
              <input className="form-input" type="number" value={age}
                     onChange={e => setAge(e.target.value)} placeholder="25" min={1} max={150} required />
            </div>
            <div className="form-group">
              <label className="form-label">Height (cm)</label>
              <input className="form-input" type="number" value={heightCm}
                     onChange={e => setHeightCm(e.target.value)} placeholder="175" min={50} max={300} required />
            </div>
            <div className="form-group">
              <label className="form-label">Weight (kg)</label>
              <input className="form-input" type="number" value={weightKg}
                     onChange={e => setWeightKg(e.target.value)} placeholder="70" min={20} max={500} required />
            </div>
          </div>

          {/* Activity Level */}
          <div className="questionnaire-step">
            <label className="form-label">Activity Level</label>
            <div className="radio-group">
              {[
                ['SEDENTARY', '🪑 Sedentary'],
                ['LIGHTLY_ACTIVE', '🚶 Lightly Active'],
                ['MODERATELY_ACTIVE', '🏃 Moderately Active'],
                ['VERY_ACTIVE', '💪 Very Active']
              ].map(([val, label]) => (
                <div key={val} className={`radio-option ${activityLevel === val ? 'selected' : ''}`}
                     onClick={() => setActivityLevel(val)}>{label}</div>
              ))}
            </div>
          </div>

          {/* Goal */}
          <div className="questionnaire-step">
            <label className="form-label">Goal</label>
            <div className="radio-group">
              {[
                ['FAT_LOSS', '🔥 Fat Loss'],
                ['WEIGHT_MAINTENANCE', '⚖️ Maintain Weight'],
                ['MUSCLE_GAIN', '💪 Muscle Gain']
              ].map(([val, label]) => (
                <div key={val} className={`radio-option ${goal === val ? 'selected' : ''}`}
                     onClick={() => setGoal(val)}>{label}</div>
              ))}
            </div>
          </div>

          <button className="btn btn-primary btn-lg" type="submit" disabled={loading}
                  style={{ width: '100%', marginTop: 16 }}>
            {loading ? 'Calculating targets...' : 'Calculate My Targets →'}
          </button>
        </form>
      </div>
    </div>
  );
}
