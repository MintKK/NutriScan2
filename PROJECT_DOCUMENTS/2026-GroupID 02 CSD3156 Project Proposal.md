---
title: Project Proposal

---

| Project Proposal II - Cloud Computing<br><br>NutriScan Cloud: AI-Powered Nutrition Analytics Web Platform<br><br> <br><br>Team number: 02<br><br>Student names: Fang Che Ee (2301504), Min Khant Ko (2301320)|
| ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 17/03/26                                                                                                                                                          |
|                                                                                                                                                                                |

# 1. Introduction

Manual food logging remains the primary barrier to sustained dietary tracking, with studies showing over 50% of users abandon calorie-counting apps within two weeks (Cordeiro et al., 2015). Our existing Android application, **NutriScan**, addressed this through on-device TensorFlow Lite food recognition, a curated 1,975-item nutrition database, and an algorithmic AI coaching engine. This Part II project re-architects NutriScan as a **cloud-native web application**—**NutriScan Cloud**—migrating the proven ML inference pipeline, nutrition matching logic, social community features, and personalized coaching from a single-device Android app to a scalable, multi-user web platform.

**Problem.** NutriScan's current architecture confines all ML inference, data persistence, and analytics to a single Android device. Users cannot access their nutrition history from a browser, insights are not shareable across devices, and the social feed (currently backed by Firebase Firestore) lacks a proper web client. Migrating to a cloud web architecture solves these limitations while enabling horizontal scaling, centralized model serving, and cross-platform accessibility.

**Goals and Scope.** NutriScan Cloud will deliver:
- **Browser-based food image upload** with server-side AI classification using the existing Food-11 TFLite model (converted to TensorFlow Serving or ONNX Runtime).
- **Personalized nutrition tracking** reusing the Mifflin-St Jeor TDEE engine and macro-split logic already implemented in `NutritionCalculator.kt`.
- **AI-powered dietary coaching** migrating the heuristic insight generators from `AICoachRepository.kt` (time-of-day advice, macro deficiency alerts, smart food swap suggestions) to a cloud API.
- **Social community features** (posts with auto-calculated macros, likes, comments, follow graph) leveraging the existing Firestore schema design.
- **Nutrition analytics dashboard** with historical trend visualization.

Features explicitly **de-scoped** from this cloud migration include on-device step counting (hardware pedometer / accelerometer), Android Activity Recognition, foreground service tracking, barcode scanning via ML Kit, OCR label scanning, the native PDF export engine, and water-tracking canvas animations—all of which are tightly coupled to Android sensor APIs or native rendering and are not justified for a web-first delivery within the project timeline.

# 2. Proposed Design and Components

## 2.1 Architecture Overview

NutriScan Cloud adopts a **three-tier cloud architecture**: a React single-page application (SPA) frontend, a stateless Kotlin Spring Boot REST API backend, and managed cloud services for storage, database, and ML inference.

```
┌───────────────────────────────────────────────────────────────┐
│                     FRONTEND (React SPA)                      │
│   Authentication UI · Upload · Dashboard · Analytics · Feed   │
└──────────────────────────┬────────────────────────────────────┘
                           │ HTTPS / REST
┌──────────────────────────▼────────────────────────────────────┐
│               BACKEND API (Kotlin Spring Boot)                │
│  Auth Controller · Meal Controller · Social Controller        │
│  AI Coach Service · Nutrition Calculator Service              │
│  Food Matching Service (LabelNormalizer + AliasIndex)         │
└───┬──────────┬─────────────┬───────────────┬──────────────────┘
    │          │             │               │
    ▼          ▼             ▼               ▼
┌────────┐ ┌────────┐ ┌──────────┐  ┌───────────────────┐
│Firebase│ │Cloud   │ │Cloud     │  │ML Inference       │
│Auth    │ │Storage │ │Firestore │  │Service (TF Serving│
│        │ │(Images)│ │(Social + │  │/ ONNX on Cloud    │
│        │ │        │ │User Data)│  │Run / GKE)         │
└────────┘ └────────┘ └──────────┘  └───────────────────┘
```

## 2.2 Component Breakdown

| Component | Technology | Responsibility |
|-----------|-----------|----------------|
| **Web Frontend** | React + TypeScript, Chart.js | User-facing SPA: authentication forms, drag-and-drop food image upload, real-time dashboard (calorie ring, macro bars), 7-day analytics charts, social feed (post/like/comment), user profile management. |
| **Backend API** | Kotlin Spring Boot (WebFlux) | Stateless REST API layer. Hosts ported business logic: `NutritionCalculator` (TDEE/BMR), `FoodMatchingService` (4-strategy ranked matching), `AICoachRepository` (heuristic insight engine, smart swap map), and `CalculateNutritionUseCase` (portion-scaled macro computation). Validates requests, enforces authorization, and orchestrates calls to cloud services. |
| **ML Inference Service** | TensorFlow Serving (Docker) on Cloud Run | Serves the existing `food11.tflite` model (converted to SavedModel format). Receives preprocessed image tensors from the backend, returns classification labels and confidence scores. Deployed as a separate containerized microservice for independent scaling. |
| **Authentication** | Firebase Authentication | Email/password sign-up and sign-in, JWT token issuance. The backend validates Firebase ID tokens on every request. Directly reuses the existing Firebase Auth project and Firestore security rules. |
| **Cloud Database** | Cloud Firestore | Stores user profiles, meal logs (with pre-computed nutrition totals), social posts, likes, comments, and follow relationships. Schema mirrors the proven NoSQL design from the Android app: denormalized user data in posts/comments for O(1) feed rendering, composite-key documents for likes and follows. |
| **Object Storage** | Google Cloud Storage / Cloudinary | Stores uploaded food images. The existing Cloudinary integration (`CloudinaryRepository.kt`) is preserved. Signed URLs ensure secure, time-limited access. |
| **Food Database** | Seeded into Firestore or PostgreSQL | The existing `food_items.json` (1,975 items with per-100g kcal, protein, carbs, fat, and aliases) is loaded at deployment. The `FoodAliasIndex` is rebuilt in-memory on backend startup for O(1) lookups. |

## 2.3 Technology Stack Justification

**Kotlin Spring Boot** is chosen for the backend because the existing NutriScan business logic (matching service, nutrition calculator, AI coach, label normalizer) is already written in Kotlin. Direct code reuse—not rewrite—minimizes risk and development time. Spring Boot WebFlux provides non-blocking request handling for concurrent image upload and inference calls.

**React** is chosen for the frontend because it is the industry standard for SPAs with complex interactive dashboards, and both team members have existing web development experience.

**TensorFlow Serving on Cloud Run** allows the ML model to be independently deployed, versioned, and scaled without redeploying the entire backend.

## 2.4 Core User Flow

1. **Sign Up / Sign In** → Firebase Auth issues JWT → stored in browser.
2. **Upload Food Image** → Image sent to backend → backend stores image in Cloud Storage → sends tensor to ML Inference Service → receives classification results.
3. **Food Matching** → Backend runs `LabelNormalizer` + `FoodMatchingService` (exact → alias → token → partial matching) against the in-memory `FoodAliasIndex` → returns ranked candidates with combined scores.
4. **Confirm & Log** → User selects food, chooses portion → `CalculateNutritionUseCase` computes scaled macros → meal log persisted to Firestore with pre-computed totals.
5. **Dashboard Update** → Frontend reactively fetches updated daily totals, calorie ring, and macro breakdown.
6. **AI Coach Tips** → Backend evaluates `AICoachRepository` heuristics (time-aware greetings, macro deficiency alerts, smart swaps) → returns top-3 contextual insights.
7. **Social Post** → User shares meal → auto-attaches food name + macros (as in `SocialRepository.createPost`) → visible in community feed sorted by `trendingScore`.

## 2.5 Cloud Properties

**Scalability.** The backend is stateless—any instance can serve any request, enabling horizontal auto-scaling behind a load balancer. The ML inference service runs as a separate Cloud Run container with concurrency-based scaling: during usage spikes (e.g., mealtimes), additional instances spin up automatically, then scale to zero during idle periods. Firestore provides automatic sharding of read/write throughput. The in-memory `FoodAliasIndex` (built once on startup from the 1,975-item dataset) avoids database bottlenecks during high-frequency classification requests.

**Reliability.** Managed cloud services (Firestore, Cloud Run, Cloud Storage) provide built-in replication, automatic failover, and 99.95% SLA. The backend implements health-check endpoints for load balancer liveness/readiness probes. ML inference failures are isolated—the backend returns cached low-confidence results or prompts manual food search as a graceful degradation path, mirroring the existing `ClassificationStatus.ERROR` handling. Firestore's atomic transactions (used for like/comment operations) prevent data inconsistency, directly preserving the `FieldValue.increment` and `runTransaction` patterns from the existing `SocialRepository`.

**Elasticity.** Cloud Run auto-scales containers from zero to N based on incoming request volume, with configurable concurrency limits. This is particularly effective for the ML inference service, where compute demand fluctuates with user activity. The frontend is served as static files from a CDN, requiring no per-user server resources.

**Security.** Firebase Authentication handles credential management with bcrypt password hashing. All API endpoints validate Firebase ID tokens before processing. HTTPS is enforced end-to-end. Uploaded images undergo file-type validation (MIME type whitelist: JPEG, PNG, WebP) and size limits (10 MB max) to prevent malicious uploads. Cloud Storage objects use signed URLs with 1-hour expiration. Firestore security rules enforce per-user read/write authorization. The backend applies rate limiting on authentication and upload endpoints to mitigate abuse.

## 2.6 Feature Migration Summary

| Category | Features Carried Over | Adapted/New for Cloud | De-scoped |
|----------|----------------------|----------------------|-----------|
| **ML/Vision** | Food-11 TFLite model, label normalizer, alias index, 4-strategy matching | Server-side inference (TF Serving), browser image upload | Barcode scanning (ML Kit), OCR label scanning |
| **Nutrition** | Mifflin-St Jeor TDEE engine, macro-split calculator, portion scaling | Cloud-persisted meal logs, cross-device sync | — |
| **Coaching** | AI Coach heuristic engine, smart swap suggestions, time-of-day insights | Server-side generation, web dashboard display | — |
| **Social** | Post/like/comment/follow schema, trending score algorithm, user search | Web feed UI, Firestore real-time listeners via REST/WebSocket | WorkManager background trending recalculation (replaced by Cloud Scheduler) |
| **Analytics** | 7-day calorie trends, macro breakdowns | Chart.js web charts, food diary drill-downs | Native PDF export, canvas water animation |
| **Fitness** | — | — | Step counter, accelerometer, Activity Recognition, foreground service |

# 3. Schedule

The compressed 3-week timeline is feasible because the project directly reuses existing Kotlin business logic, an already-configured Firebase project, proven data models, and a curated ML model and nutrition dataset—eliminating the research and prototyping phases typical of greenfield projects. LLM-assisted development further accelerates boilerplate generation, code porting, and frontend scaffolding.

| Week | Dates | Milestone | Deliverable |
|------|-------|-----------|-------------|
| 11 | 17–23 Mar | **Infrastructure, ML Service & Core Backend** | GCP project + Firebase Auth/Firestore initialized. Spring Boot backend scaffolded with auth token validation. TFLite model converted and deployed on Cloud Run (TF Serving). `FoodMatchingService`, `LabelNormalizer`, `NutritionCalculator`, and `AICoachRepository` ported to backend. Image upload endpoint with Cloud Storage. React SPA scaffolded with auth flow and routing. |
| 12 | 24–30 Mar | **Frontend Features & Social Module** | Frontend: image upload → classification → confirm → meal logging flow. Dashboard (calorie ring, macro breakdown, AI Coach insight cards). 7-day analytics charts (Chart.js) and food diary drill-down. Social API (posts, likes, comments, follow/unfollow) ported from `SocialRepository`. Community feed, user profiles, and user search UI. |
| 13 | 31 Mar–5 Apr | **Integration, Security & Demo** | End-to-end integration testing. Security hardening (rate limiting, input validation, HTTPS enforcement). UI polish and responsive design. Final demo preparation and documentation. **Deadline: 5 April 2026.** |

# 4. Team Members and Roles

| **SN** | **Name** | **Student ID** | **Responsible Components** |
| ------ | -------- | -------------- | -------------------------- |
| 1 | Fang Che Ee | 2301504 | **Cloud ML & Backend Lead** — Port TFLite model to TF Serving, implement food classification API, migrate `FoodMatchingService`, `LabelNormalizer`, `AICoachRepository`, and `NutritionCalculator` to Spring Boot backend. Cloud infrastructure setup (Cloud Run, CI/CD). Backend security (token validation, rate limiting). |
| 2 | Min Khant Ko | 2301320 | **Frontend & Social Lead** — Build React SPA (authentication, image upload, dashboard, analytics charts, food diary). Implement social feed UI (posts, likes, comments, profiles). Port Firestore social schema and real-time data flows to web client. End-to-end testing with Cypress. |