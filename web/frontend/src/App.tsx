import { BrowserRouter, Routes, Route, Navigate, NavLink, useLocation } from 'react-router-dom';
import { AuthProvider, useAuth } from './hooks/useAuth';
import LoginPage from './pages/LoginPage';
import SignUpPage from './pages/SignUpPage';
import DashboardPage from './pages/DashboardPage';
import AddMealPage from './pages/AddMealPage';
import AnalyticsPage from './pages/AnalyticsPage';
import SocialPage from './pages/SocialPage';
import QuestionnairePage from './pages/QuestionnairePage';
import './index.css';

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth();
  if (loading) return <div className="spinner" />;
  if (!user) return <Navigate to="/login" />;
  return <>{children}</>;
}

function Navbar() {
  const { user, logout } = useAuth();
  const location = useLocation();

  // Don't show navbar on auth/questionnaire pages
  if (['/login', '/signup', '/questionnaire'].includes(location.pathname)) return null;
  if (!user) return null;

  return (
    <nav className="navbar">
      <NavLink to="/" className="navbar-brand">
        <div className="logo-icon">🥗</div>
        NutriScan Cloud
      </NavLink>
      <div className="navbar-links">
        <NavLink to="/" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`} end>
          Dashboard
        </NavLink>
        <NavLink to="/add-meal" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
          Add Meal
        </NavLink>
        <NavLink to="/analytics" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
          Analytics
        </NavLink>
        <NavLink to="/social" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
          Community
        </NavLink>
      </div>
      <div className="navbar-user">
        <div className="user-avatar">
          {(user.email || '?')[0].toUpperCase()}
        </div>
        <button className="btn btn-ghost btn-sm" onClick={logout}>
          Logout
        </button>
      </div>
    </nav>
  );
}

function AppContent() {
  return (
    <>
      <Navbar />
      <div className="app-content">
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/signup" element={<SignUpPage />} />
          <Route path="/questionnaire" element={
            <ProtectedRoute><QuestionnairePage /></ProtectedRoute>
          } />
          <Route path="/" element={
            <ProtectedRoute><DashboardPage /></ProtectedRoute>
          } />
          <Route path="/add-meal" element={
            <ProtectedRoute><AddMealPage /></ProtectedRoute>
          } />
          <Route path="/analytics" element={
            <ProtectedRoute><AnalyticsPage /></ProtectedRoute>
          } />
          <Route path="/social" element={
            <ProtectedRoute><SocialPage /></ProtectedRoute>
          } />
          <Route path="*" element={<Navigate to="/" />} />
        </Routes>
      </div>
    </>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <div className="app-layout">
          <AppContent />
        </div>
      </AuthProvider>
    </BrowserRouter>
  );
}
