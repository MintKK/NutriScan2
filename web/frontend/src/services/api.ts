import axios from 'axios';
import { auth } from './firebase';

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:3001';

const api = axios.create({
  baseURL: `${API_BASE}/api`,
  timeout: 30000,
});

// Attach Firebase ID token to every request
api.interceptors.request.use(async (config) => {
  const user = auth.currentUser;
  if (user) {
    const token = await user.getIdToken();
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// ============ AUTH ============

export const authApi = {
  getProfile: () => api.get('/auth/profile'),
  createProfile: (data: { username: string; displayname?: string; bio?: string }) =>
    api.post('/auth/profile', data),
  saveQuestionnaire: (data: {
    gender: string; age: number; heightCm: number;
    weightKg: number; activityLevel: string; goal: string;
  }) => api.post('/auth/questionnaire', data),
};

// ============ CLASSIFY ============

export const classifyApi = {
  classifyResults: (results: { label: string; confidence: number }[]) => 
    api.post('/classify', { results }),
  searchFood: (query: string) => api.get(`/classify/search?q=${encodeURIComponent(query)}`),
  calculatePortion: (foodName: string, grams: number) =>
    api.post('/classify/portion', { foodName, grams }),
};

// ============ MEALS ============

export const mealsApi = {
  logMeal: (data: {
    foodName: string; kcalPer100g: number; proteinPer100g: number;
    carbsPer100g: number; fatPer100g: number; grams: number; imageUrl?: string;
  }) => api.post('/meals', data),
  getToday: () => api.get('/meals/today'),
  getWeekly: () => api.get('/meals/weekly'),
  getDiary: (date: string) => api.get(`/meals/diary/${date}`),
  deleteMeal: (id: string) => api.delete(`/meals/${id}`),
  getInsights: () => api.get('/meals/insights'),
};

// ============ SOCIAL ============

export const socialApi = {
  getFeed: (sort?: string) => api.get(`/social/feed${sort ? `?sort=${sort}` : ''}`),
  createPost: (data: {
    caption: string; foodName: string; calories: number;
    protein: number; carbs: number; fat: number; foodImageUrl: string;
  }) => api.post('/social/posts', data),
  deletePost: (id: string) => api.delete(`/social/posts/${id}`),
  toggleLike: (postId: string) => api.post(`/social/posts/${postId}/like`),
  getComments: (postId: string) => api.get(`/social/posts/${postId}/comments`),
  addComment: (postId: string, content: string) =>
    api.post(`/social/posts/${postId}/comments`, { content }),
  toggleFollow: (uid: string) => api.post(`/social/follow/${uid}`),
  getFollowStatus: (uid: string) => api.get(`/social/follow/status/${uid}`),
  searchUsers: (query: string) => api.get(`/social/users/search?q=${encodeURIComponent(query)}`),
  getUserProfile: (uid: string) => api.get(`/social/users/${uid}`),
  getUserPosts: (uid: string) => api.get(`/social/users/${uid}/posts`),
};

// ============ UPLOAD ============

export const uploadApi = {
  uploadImage: (file: File) => {
    const formData = new FormData();
    formData.append('image', file);
    return api.post('/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 60000,
    });
  },
};

export default api;
