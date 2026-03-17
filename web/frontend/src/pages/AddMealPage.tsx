import { useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { classifyApi, mealsApi } from '../services/api';

interface FoodCandidate {
  food: { name: string; kcalPer100g: number; proteinPer100g: number; carbsPer100g: number; fatPer100g: number };
  confidence: number;
  combinedScore: number;
  mlLabel: string;
  matchType: string;
  portions: Record<string, { kcal: number; protein: number; carbs: number; fat: number }>;
  coachTip: { emoji: string; message: string } | null;
}

export default function AddMealPage() {
  const navigate = useNavigate();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [step, setStep] = useState<'upload' | 'results' | 'portion' | 'search'>('upload');
  const [classifying, setClassifying] = useState(false);
  const [candidates, setCandidates] = useState<FoodCandidate[]>([]);
  const [selected, setSelected] = useState<FoodCandidate | null>(null);
  const [selectedPortion, setSelectedPortion] = useState('150g');
  const [customGrams, setCustomGrams] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<any[]>([]);
  const [searching, setSearching] = useState(false);
  const [previewUrl, setPreviewUrl] = useState('');
  const [dragging, setDragging] = useState(false);
  const [logging, setLogging] = useState(false);
  const [classifyMessage, setClassifyMessage] = useState('');

  const handleFile = async (file: File) => {
    setPreviewUrl(URL.createObjectURL(file));
    setClassifying(true);
    setClassifyMessage('');
    try {
      const res = await classifyApi.classifyImage(file);
      const data = res.data;
      if (data.candidates && data.candidates.length > 0) {
        setCandidates(data.candidates);
        setStep('results');
      } else {
        setClassifyMessage(data.message || 'No food detected. Try searching manually.');
        setStep('search');
      }
    } catch (err) {
      setClassifyMessage('Classification failed. Try searching manually.');
      setStep('search');
    } finally {
      setClassifying(false);
    }
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setDragging(false);
    const file = e.dataTransfer.files[0];
    if (file && file.type.startsWith('image/')) handleFile(file);
  };

  const handleSearch = async () => {
    if (searchQuery.length < 2) return;
    setSearching(true);
    try {
      const res = await classifyApi.searchFood(searchQuery);
      setSearchResults(res.data.results || []);
    } catch (err) {
      console.error('Search error:', err);
    } finally {
      setSearching(false);
    }
  };

  const selectCandidate = (c: FoodCandidate) => {
    setSelected(c);
    setStep('portion');
  };

  const selectSearchResult = (r: any) => {
    setSelected({
      food: r.food,
      confidence: 100,
      combinedScore: 100,
      mlLabel: 'Manual Search',
      matchType: 'EXACT',
      portions: r.portions,
      coachTip: null
    });
    setStep('portion');
  };

  const getPortionNutrition = () => {
    if (!selected) return null;
    if (customGrams) {
      const g = parseInt(customGrams);
      if (!g || g <= 0) return null;
      const f = selected.food;
      const factor = g / 100;
      return {
        kcal: Math.round(f.kcalPer100g * factor),
        protein: parseFloat((f.proteinPer100g * factor).toFixed(1)),
        carbs: parseFloat((f.carbsPer100g * factor).toFixed(1)),
        fat: parseFloat((f.fatPer100g * factor).toFixed(1)),
        grams: g
      };
    }
    const nutrition = selected.portions[selectedPortion];
    const grams = parseInt(selectedPortion) || 100;
    return nutrition ? { ...nutrition, grams } : null;
  };

  const logMeal = async () => {
    if (!selected) return;
    const nutrition = getPortionNutrition();
    if (!nutrition) return;
    setLogging(true);
    try {
      await mealsApi.logMeal({
        foodName: selected.food.name,
        kcalPer100g: selected.food.kcalPer100g,
        proteinPer100g: selected.food.proteinPer100g,
        carbsPer100g: selected.food.carbsPer100g,
        fatPer100g: selected.food.fatPer100g,
        grams: nutrition.grams,
      });
      navigate('/');
    } catch (err) {
      console.error('Log error:', err);
    } finally {
      setLogging(false);
    }
  };

  const portionNutrition = getPortionNutrition();

  return (
    <div className="slide-up" style={{ maxWidth: 700, margin: '0 auto' }}>
      <div className="page-header">
        <h1 className="page-title">Add Meal</h1>
        <div style={{ display: 'flex', gap: 8 }}>
          <button className="btn btn-ghost" onClick={() => setStep('search')}>🔍 Search</button>
          <button className="btn btn-ghost" onClick={() => { setStep('upload'); setCandidates([]); setSelected(null); }}>📷 Photo</button>
        </div>
      </div>

      {/* Step 1: Upload */}
      {step === 'upload' && (
        <>
          <div
            className={`upload-zone ${dragging ? 'dragging' : ''}`}
            onDragOver={e => { e.preventDefault(); setDragging(true); }}
            onDragLeave={() => setDragging(false)}
            onDrop={handleDrop}
            onClick={() => fileInputRef.current?.click()}
          >
            {classifying ? (
              <>
                <div className="spinner" style={{ margin: '0 auto 16px' }} />
                <div className="upload-text">Analyzing your food...</div>
                <div className="upload-subtext">Running AI classification</div>
              </>
            ) : previewUrl ? (
              <img src={previewUrl} alt="Preview"
                style={{ maxHeight: 200, borderRadius: 12, objectFit: 'cover' }} />
            ) : (
              <>
                <div className="upload-icon">📸</div>
                <div className="upload-text">Upload a food photo</div>
                <div className="upload-subtext">Drag & drop or click to browse · JPEG, PNG, WebP · Max 10MB</div>
              </>
            )}
          </div>
          <input
            ref={fileInputRef}
            type="file"
            accept="image/jpeg,image/png,image/webp"
            style={{ display: 'none' }}
            onChange={e => e.target.files?.[0] && handleFile(e.target.files[0])}
          />
          {classifyMessage && (
            <div className="coach-tip" style={{ marginTop: 16 }}>
              <span className="tip-icon">💡</span>
              <span>{classifyMessage}</span>
            </div>
          )}
        </>
      )}

      {/* Step 2: Classification Results */}
      {step === 'results' && (
        <div className="food-candidates">
          <p style={{ color: 'var(--text-secondary)', marginBottom: 8 }}>
            We found {candidates.length} match{candidates.length !== 1 ? 'es' : ''}. Tap to select:
          </p>
          {candidates.map((c, i) => (
            <div
              key={i}
              className={`food-candidate ${selected === c ? 'selected' : ''}`}
              onClick={() => selectCandidate(c)}
            >
              <div className="food-info">
                <div className="food-name">{c.food.name}</div>
                <div className="food-macros">
                  {c.food.kcalPer100g} kcal/100g · P:{c.food.proteinPer100g}g · C:{c.food.carbsPer100g}g · F:{c.food.fatPer100g}g
                </div>
              </div>
              <span className="confidence-badge">{c.confidence}%</span>
            </div>
          ))}
          <button className="btn btn-ghost" onClick={() => setStep('search')} style={{ marginTop: 8 }}>
            Not what you're looking for? Search manually →
          </button>
        </div>
      )}

      {/* Search */}
      {step === 'search' && (
        <div>
          <div className="search-bar" style={{ marginBottom: 16 }}>
            <span className="search-icon">🔍</span>
            <input
              id="food-search"
              placeholder="Search foods (e.g. chicken, rice, apple)..."
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && handleSearch()}
            />
          </div>
          <button className="btn btn-primary btn-sm" onClick={handleSearch} disabled={searching}>
            {searching ? 'Searching...' : 'Search'}
          </button>

          <div className="food-candidates" style={{ marginTop: 16 }}>
            {searchResults.map((r, i) => (
              <div key={i} className="food-candidate" onClick={() => selectSearchResult(r)}>
                <div className="food-info">
                  <div className="food-name">{r.food.name}</div>
                  <div className="food-macros">
                    {r.food.kcalPer100g} kcal/100g · P:{r.food.proteinPer100g}g · C:{r.food.carbsPer100g}g · F:{r.food.fatPer100g}g
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Step 3: Portion Picker */}
      {step === 'portion' && selected && (
        <div className="card" style={{ marginTop: 16 }}>
          <h3 style={{ marginBottom: 4 }}>{selected.food.name}</h3>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginBottom: 16 }}>
            {selected.food.kcalPer100g} kcal per 100g
          </p>

          {selected.coachTip && (
            <div className="coach-tip" style={{ marginBottom: 16 }}>
              <span className="tip-icon">{selected.coachTip.emoji}</span>
              <span>{selected.coachTip.message}</span>
            </div>
          )}

          <label className="form-label">Select portion size</label>
          <div className="portion-options">
            {Object.keys(selected.portions).map(p => (
              <button
                key={p}
                className={`portion-btn ${selectedPortion === p && !customGrams ? 'active' : ''}`}
                onClick={() => { setSelectedPortion(p); setCustomGrams(''); }}
              >
                {p}
              </button>
            ))}
          </div>

          <div className="form-group" style={{ marginTop: 12 }}>
            <label className="form-label">Or enter custom grams</label>
            <input
              className="form-input"
              type="number"
              placeholder="e.g. 200"
              value={customGrams}
              onChange={e => setCustomGrams(e.target.value)}
              min={1}
              max={5000}
            />
          </div>

          {portionNutrition && (
            <div style={{
              display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12,
              background: 'var(--bg-elevated)', borderRadius: 'var(--radius-md)',
              padding: 16, margin: '16px 0', textAlign: 'center'
            }}>
              <div>
                <div style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--primary-light)' }}>{portionNutrition.kcal}</div>
                <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>kcal</div>
              </div>
              <div>
                <div style={{ fontSize: '1.25rem', fontWeight: 700, color: '#60a5fa' }}>{portionNutrition.protein}g</div>
                <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Protein</div>
              </div>
              <div>
                <div style={{ fontSize: '1.25rem', fontWeight: 700, color: '#fbbf24' }}>{portionNutrition.carbs}g</div>
                <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Carbs</div>
              </div>
              <div>
                <div style={{ fontSize: '1.25rem', fontWeight: 700, color: '#f87171' }}>{portionNutrition.fat}g</div>
                <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Fat</div>
              </div>
            </div>
          )}

          <div style={{ display: 'flex', gap: 12, marginTop: 16 }}>
            <button className="btn btn-secondary" onClick={() => setStep('results')}>← Back</button>
            <button
              className="btn btn-primary"
              style={{ flex: 1 }}
              onClick={logMeal}
              disabled={logging || !portionNutrition}
            >
              {logging ? 'Logging...' : `Log Meal (${portionNutrition?.kcal || 0} kcal)`}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
