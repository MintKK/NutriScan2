# NutriScan Web App

NutriScan is a cloud-based food recognition and nutrition tracking web application. It allows users to upload food images, perform classification using machine learning, and retrieve nutritional information for analysis.

---

## Project Structure

```
NutriScan2/
└── web/
    ├── backend/     # Backend API (e.g. Flask / server logic)
    └── frontend/    # Vite-based frontend application
```

---

## How to Run the Application

To run the application locally, both the frontend and backend components must be started.

### Frontend (Vite)

Navigate to the frontend directory:

```
cd ...\NutriScan2\web\frontend
```

Install the required dependencies:

```
npm install
```

Start the Vite development server:

```
npm run dev
```

Once the server starts, open your browser and access:

```
http://localhost:5173
```

---

### Backend (Optional but Recommended)

If your application relies on backend APIs, start the backend server as well.

Navigate to the backend directory:

```
cd C:\Users\CheEe.Fang\AndroidStudioProjects\NutriScan2\web\backend
```

Run the backend server (example using Flask):

```
python app.py
```

Ensure the backend is running before using features such as image processing or nutrition retrieval.

---

## Important Notes

- All npm commands must be executed inside the `frontend` directory where the `package.json` file is located.
- Running npm commands in the root project directory will result in a `ENOENT: package.json not found` error.
- Always run `npm install` before starting the development server if dependencies have not been installed.

---

## Troubleshooting

- **Error: ENOENT: no such file or directory, package.json**  
  This indicates that you are in the wrong directory. Ensure you are inside the `frontend` folder before running npm commands.

- **Localhost not loading**  
  Ensure the Vite server is running and no other applications are using port 5173.

- **Backend-related errors**  
  Ensure the backend server is running and accessible.

---

## Tech Stack

- Frontend: Vite + React
- Backend: Flask (or equivalent server framework)
- Machine Learning: TensorFlow Lite / Image Classification
- Database: Local storage / SQLite (if applicable)

---

## Status

This project is currently in development and serves as a functional prototype demonstrating end-to-end integration of frontend, backend, and machine learning components.
