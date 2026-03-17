import { useState, useEffect } from 'react';
import { mealsApi } from '../services/api';
import { Bar } from 'react-chartjs-2';
import {
  Chart as ChartJS, CategoryScale, LinearScale, BarElement,
  Title, Tooltip, Legend
} from 'chart.js';

ChartJS.register(CategoryScale, LinearScale, BarElement, Title, Tooltip, Legend);

interface DayData {
  date: string;
  dayLabel: string;
  kcal: number;
  protein: number;
  carbs: number;
  fat: number;
  mealCount: number;
}

export default function AnalyticsPage() {
  const [weeklyData, setWeeklyData] = useState<DayData[]>([]);
  const [selectedDate, setSelectedDate] = useState('');
  const [diaryMeals, setDiaryMeals] = useState<any[]>([]);
  const [diaryTotals, setDiaryTotals] = useState({ kcal: 0, protein: 0, carbs: 0, fat: 0 });
  const [loading, setLoading] = useState(true);

  useEffect(() => { loadWeekly(); }, []);

  const loadWeekly = async () => {
    try {
      const res = await mealsApi.getWeekly();
      setWeeklyData(res.data.weeklyData || []);
    } catch (err) {
      console.error('Weekly load error:', err);
    } finally {
      setLoading(false);
    }
  };

  const loadDiary = async (date: string) => {
    setSelectedDate(date);
    try {
      const res = await mealsApi.getDiary(date);
      setDiaryMeals(res.data.meals || []);
      setDiaryTotals(res.data.totals || { kcal: 0, protein: 0, carbs: 0, fat: 0 });
    } catch (err) {
      console.error('Diary load error:', err);
    }
  };

  const chartData = {
    labels: weeklyData.map(d => d.dayLabel),
    datasets: [
      {
        label: 'Calories',
        data: weeklyData.map(d => d.kcal),
        backgroundColor: weeklyData.map(d =>
          d.kcal > 2500 ? 'rgba(239, 68, 68, 0.7)' :
          d.kcal > 1800 ? 'rgba(16, 185, 129, 0.7)' :
          'rgba(59, 130, 246, 0.5)'
        ),
        borderRadius: 8,
        borderSkipped: false,
      }
    ]
  };

  const chartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      tooltip: {
        backgroundColor: '#1e293b',
        titleColor: '#f1f5f9',
        bodyColor: '#94a3b8',
        borderColor: '#334155',
        borderWidth: 1,
        padding: 12,
        cornerRadius: 8,
      }
    },
    scales: {
      x: {
        grid: { display: false },
        ticks: { color: '#64748b', font: { family: 'Inter' } }
      },
      y: {
        grid: { color: 'rgba(51,65,85,0.5)' },
        ticks: { color: '#64748b', font: { family: 'Inter' } }
      }
    }
  };

  if (loading) return <div className="spinner" />;

  return (
    <div className="slide-up">
      <div className="page-header">
        <h1 className="page-title">Analytics</h1>
      </div>

      {/* Weekly Chart */}
      <div className="card" style={{ marginBottom: 24 }}>
        <div className="card-header">
          <span className="card-title">📊 7-Day Calorie Trend</span>
        </div>
        <div style={{ height: 280 }}>
          <Bar data={chartData} options={chartOptions} />
        </div>
      </div>

      {/* Weekly Summary */}
      <div className="card" style={{ marginBottom: 24 }}>
        <div className="card-header">
          <span className="card-title">📅 Daily Breakdown</span>
        </div>
        {weeklyData.map(d => (
          <div
            key={d.date}
            className="meal-item"
            style={{ cursor: 'pointer' }}
            onClick={() => loadDiary(d.date)}
          >
            <div className="meal-icon">{d.mealCount > 0 ? '📋' : '—'}</div>
            <div className="meal-details">
              <div className="meal-name">{d.dayLabel} · {d.date}</div>
              <div className="meal-meta">
                {d.mealCount} meal{d.mealCount !== 1 ? 's' : ''} ·
                P:{Math.round(d.protein)}g · C:{Math.round(d.carbs)}g · F:{Math.round(d.fat)}g
              </div>
            </div>
            <div className="meal-kcal">{d.kcal} kcal</div>
          </div>
        ))}
      </div>

      {/* Diary Drill-down */}
      {selectedDate && (
        <div className="card animate-in">
          <div className="card-header">
            <span className="card-title">🍽️ Meals on {selectedDate}</span>
            <button className="btn btn-ghost btn-sm" onClick={() => setSelectedDate('')}>✕</button>
          </div>
          {diaryMeals.length > 0 ? (
            <>
              <div style={{
                display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12,
                background: 'var(--bg-elevated)', borderRadius: 'var(--radius-md)',
                padding: 12, marginBottom: 16, textAlign: 'center', fontSize: '0.85rem'
              }}>
                <div><strong style={{ color: 'var(--primary-light)' }}>{diaryTotals.kcal}</strong><br />kcal</div>
                <div><strong style={{ color: '#60a5fa' }}>{Math.round(diaryTotals.protein)}g</strong><br />Protein</div>
                <div><strong style={{ color: '#fbbf24' }}>{Math.round(diaryTotals.carbs)}g</strong><br />Carbs</div>
                <div><strong style={{ color: '#f87171' }}>{Math.round(diaryTotals.fat)}g</strong><br />Fat</div>
              </div>
              {diaryMeals.map((m: any, i: number) => (
                <div className="meal-item" key={i}>
                  <div className="meal-icon">🍲</div>
                  <div className="meal-details">
                    <div className="meal-name">{m.foodName}</div>
                    <div className="meal-meta">{m.grams}g</div>
                  </div>
                  <div className="meal-kcal">{m.kcalTotal} kcal</div>
                </div>
              ))}
            </>
          ) : (
            <div className="empty-state">
              <div className="emoji">📭</div>
              <div className="message">No meals logged on this day</div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
