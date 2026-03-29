import { initializeApp } from 'firebase/app';
import { getAuth } from 'firebase/auth';

// ============================================================
// TODO: Replace ALL values below with YOUR Firebase project config.
// Get these from Firebase Console → Project Settings → Your apps → Web app
// ============================================================
const firebaseConfig = {
  apiKey: "AIzaSyC1NktgeKSJGcetjxVhIYk5JnZxll_33RI",
  authDomain: "nutriscan-cloud.firebaseapp.com",
  projectId: "nutriscan-cloud",
  storageBucket: "nutriscan-cloud.firebasestorage.app",
  messagingSenderId: "245667741854",
  appId: "1:245667741854:web:8f90e760d7ebb202693b8f"
};


const app = initializeApp(firebaseConfig);
export const auth = getAuth(app);
export default app;

