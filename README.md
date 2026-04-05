# NutriScan Web App

NutriScan is a cloud-based food recognition and nutrition tracking web application. It allows users to upload food images, perform classification using machine learning, and retrieve nutritional information for analysis. The project is fully deployed to the cloud, taking advantage of scalable infrastructure.

---

## Live Demo

*The application is deployed and available online at: **[https://nutriscan-cloud.web.app/](https://nutriscan-cloud.web.app/)***

---

## Key Features

- **Food Recognition**: AI-powered image classification to identify food items from user uploads using TensorFlow.
- **Nutrition Analytics**: Automatically tracks macronutrients and visualizes nutritional data using interactive charts.
- **Social Community**: Users can share meals and discover meal-logging activities from others in the community tab.
- **Cloud-Native Architecture**: Built securely on Google Cloud Platform (GCP), employing RBAC (Role-Based Access Control) principles and CI/CD pipelines.

---

## Tech Stack

### Frontend
- **Framework**: React 19 + TypeScript + Vite
- **Data Visualization**: Chart.js
- **Routing**: React Router DOM v7
- **Authentication**: Firebase Authentication

### Backend
- **Server Framework**: Node.js + Express
- **Machine Learning**: TensorFlow CPU & TFLite (`@tensorflow/tfjs`) for server-side food image inference.
- **Image Processing**: Sharp & Multer
- **Database**: Google Cloud Firestore / Firebase Admin SDK

---

## Project Structure

```text
NutriScan2/
├── web/
│   ├── backend/     # Node.js + Express API and ML inference
│   └── frontend/    # Vite-based React application
└── README.md        # This repository guide
```

---

## Development Status

NutriScan Cloud is a fully functional project demonstrating the end-to-end integration of a modern React frontend, a Node.js backend API, server-side machine learning inference, and cloud infrastructure.

> **Note on Local Development**: This is a deployed cloud project. For local development or debugging, you will need to properly secure and configure `.env` files for both the frontend (Firebase public keys) and backend (Google Cloud IAM Service Account keys and database credentials).
