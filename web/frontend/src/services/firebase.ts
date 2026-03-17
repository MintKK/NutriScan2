import { initializeApp } from 'firebase/app';
import { getAuth } from 'firebase/auth';

// Firebase configuration
// API key and App ID come from registering a Web App in Firebase Console
const firebaseConfig = {
  apiKey: "AIzaSyDnBhnE5zAdYdExLhWUvWncIR6UstM4GDA",
  authDomain: "nutriscan-2c485.firebaseapp.com",
  projectId: "nutriscan-2c485",
  storageBucket: "nutriscan-2c485.firebasestorage.app",
  messagingSenderId: "443358962510",
  appId: "1:443358962510:web:2ab3a88abcd69273cb341f",
  measurementId: "G-CGSH5EDPYX"
};

const app = initializeApp(firebaseConfig);
export const auth = getAuth(app);
export default app;

