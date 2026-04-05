---
title: Project Proposal

---

| Project Proposal II - Cloud Computing<br><br>NutriScan Cloud: AI-Powered Nutrition Analytics Web Platform<br><br>Team number: 02<br><br>Student names: Fang Che Ee (2301504), Min Khant Ko (2301320)|
| ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 17/03/26                                                                                                                                                          |
|                                                                                                                                                                                |

<style>
  /* Reduce general text size and line height */
  body, p, li {
    font-size: 13px !important;
    line-height: 1.4 !important;
    margin-bottom: 8px !important;
  }
  
  /* Shrink the massive spacing around headings */
  h1 { font-size: 20px !important; margin-top: 15px !important; margin-bottom: 10px !important; }
  h2 { font-size: 16px !important; margin-top: 12px !important; margin-bottom: 8px !important; }
  
  /* Compress the tables so they don't take up whole pages */
  table { font-size: 12px !important; }
  th, td { padding: 4px 8px !important; }

  /* Shrink the ASCII architecture diagrams */
  pre, code { font-size: 11px !important; line-height: 1.2 !important; }

  /* STOPS URLS FROM PRINTING NEXT TO LINKS */
  @media print {
    a[href]::after {
      content: none !important;
    }
  }
</style>


# 1. Introduction

Manual food logging remains the primary barrier to sustained dietary tracking, with studies showing over 50% of users abandon calorie-counting apps within two weeks (Cordeiro et al., 2015). Our existing Android application, **NutriScan**, addressed this through on-device TensorFlow Lite food recognition, a curated 1,975-item nutrition database, and an algorithmic AI coaching engine. This Part II project re-architects NutriScan as **NutriScan Cloud**—a **cloud-native AI nutrition analytics platform** that combines food image recognition, personalized dietary coaching, social accountability, and cross-device persistent analytics atop Google Cloud Platform managed services. NutriScan Cloud is innovative not merely because it recognizes food images, but because it transforms a single-device nutrition tracker into a cloud-native AI platform that supports shared analytics, personalized coaching, and socially enabled accountability across devices.

**Problem.** NutriScan's current architecture confines all ML inference, data persistence, and analytics to a single Android device. Users cannot access their nutrition history from a browser, and insights are not shareable across devices. Migrating to a cloud architecture solves these limitations while enabling horizontal scaling, centralized model serving, and cross-platform accessibility.

**Goals and Scope.** NutriScan Cloud will deliver:
- **Browser-based food image upload** with server-side AI classification using the existing Food-11 model converted to TensorFlow Serving format.
- **Personalized nutrition tracking** reusing the Mifflin-St Jeor TDEE engine and macro-split logic from the existing codebase.
- **AI-powered dietary coaching** migrating the heuristic insight engine (time-of-day advice, macro deficiency alerts, smart food swap suggestions) to a cloud API.
- **Social community features** (posts with auto-calculated macros, likes, comments, follow graph) leveraging the existing Firestore schema.
- **Nutrition analytics dashboard** with historical trend visualization.

# 2. Proposed Design and Components

## 2.1 Architecture Overview

NutriScan Cloud adopts a **three-tier cloud architecture**: a React SPA frontend, a stateless Kotlin Spring Boot REST API backend, and managed GCP services for storage, database, and ML inference.

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
│        │ │(Images)│ │(User +   │  │on Cloud Run)      │
│        │ │        │ │Meal Data)│  │                   │
└────────┘ └────────┘ └──────────┘  └───────────────────┘
```

## 2.2 Component Breakdown

| Component | Technology | Responsibility |
|-----------|-----------|----------------|
| **Web Frontend** | React + TypeScript, Chart.js | SPA: authentication, food image upload, real-time dashboard (calorie ring, macro bars), 7-day analytics charts, social feed, and user profiles. |
| **Backend API** | Kotlin Spring Boot (WebFlux) | Stateless REST API hosting ported business logic: `NutritionCalculator` (TDEE/BMR), `FoodMatchingService` (4-strategy ranked matching), `AICoachRepository` (heuristic insight engine), and `CalculateNutritionUseCase` (portion-scaled macro computation). Validates requests, enforces authorization, and orchestrates cloud service calls. |
| **ML Inference Service** | TensorFlow Serving (Docker) on Cloud Run | Serves the Food-11 model (converted to SavedModel format) as a separate containerized microservice. Returns classification labels and confidence scores. Independently scalable. |
| **Authentication** | Firebase Authentication | Email/password sign-up/sign-in with JWT issuance. The backend validates Firebase ID tokens on every request. |
| **Cloud Database** | Cloud Firestore | Stores user profiles, meal logs (with pre-computed nutrition totals), social posts, likes, comments, and follow relationships. Schema mirrors the proven NoSQL design from the Android app. |
| **Object Storage** | Google Cloud Storage | Stores uploaded food images with signed URLs for secure, time-limited client access and lifecycle policies for cleanup. |
| **Food Database** | Seeded into Firestore | The existing `food_items.json` (1,975 items with per-100g macros and aliases) is loaded at deployment. The `FoodAliasIndex` is rebuilt in-memory on backend startup for O(1) lookups. |

## 2.3 Technology Stack Justification

**Kotlin Spring Boot** is chosen because the existing NutriScan business logic is already written in Kotlin—direct code reuse minimizes risk and development time. Spring Boot WebFlux provides non-blocking request handling for concurrent image upload and inference calls.

**TensorFlow Serving on Cloud Run** allows the ML model to be independently deployed, versioned, and scaled without redeploying the backend.

## 2.4 Core User Flow

1. **Sign Up / Sign In** → Firebase Auth issues JWT → stored in browser.
2. **Upload Food Image** → Backend stores image in Cloud Storage → sends tensor to ML Inference Service → receives classification results.
3. **Food Matching** → Backend runs `LabelNormalizer` + `FoodMatchingService` against the in-memory `FoodAliasIndex` → returns ranked candidates.
4. **Confirm & Log** → User selects food and portion → `CalculateNutritionUseCase` computes scaled macros → meal log persisted to Firestore.
5. **Dashboard Update** → Frontend fetches updated daily totals, calorie ring, and macro breakdown.
6. **AI Coach Tips** → Backend evaluates `AICoachRepository` heuristics → returns top-3 contextual insights.
7. **Social Post** → User shares meal with auto-attached macros → visible in community feed.

## 2.5 Cloud Properties

**Scalability.** The backend is stateless, enabling horizontal auto-scaling via Cloud Run's concurrency and instance-count settings. The ML inference service scales independently: additional instances spin up during usage spikes and scale to zero when idle. Firestore provides automatic sharding of read/write throughput.

**Reliability.** All core services (Firestore, Cloud Run, Cloud Storage) are managed with built-in replication and automatic failover. The backend exposes `/healthz` endpoints for Cloud Run's liveness and readiness probes. ML inference failures trigger graceful degradation—manual food search is offered as a fallback. Firestore atomic transactions ensure data consistency for social operations.

**Elasticity.** Cloud Run auto-scales containers from zero to N based on request volume with configurable concurrency limits. The frontend is served from Firebase Hosting's global CDN, requiring no per-user server resources.

**Security.** Firebase Authentication provides managed credential storage. All API endpoints validate Firebase ID tokens via Spring Security filters. HTTPS is terminated at Cloud Run ingress and enforced end-to-end. Uploaded images undergo MIME type validation and size limits. Cloud Storage uses signed URLs with expiration. Firestore security rules enforce per-user authorization. Secret Manager stores all credentials, eliminating hard-coded secrets.

## 2.6 Deployment and Cloud Configuration Strategy

The value of NutriScan Cloud as a cloud engineering project lies in the **deployment architecture** that achieves scalability, reliability, elasticity, and security by design.

### Request Flow

```
User Browser
    │
    ▼
Firebase Hosting (CDN, static SPA assets)
    │  HTTPS
    ▼
Cloud Run — Backend API Service (Kotlin Spring Boot container)
    ├──► Firebase Auth (token verification)
    ├──► Cloud Firestore (user, meal, social data)
    ├──► Cloud Storage (food images, signed-URL access)
    └──► Cloud Run — ML Inference Service (TF Serving container)
              └──► GCS Model Bucket (versioned SavedModel artifacts)
```

All inter-service communication uses HTTPS with IAM-authenticated service-to-service calls. The ML inference endpoint is not publicly exposed.

### Service Configuration Summary

| Service | Key Configuration | Purpose |
|---------|------------------|---------|
| **Cloud Run — Backend API** | Autoscaling bounds for low-latency API traffic (always-warm min instance, capped max), IAM invoker role restricted | Stateless API; auto-scales horizontally with request volume |
| **Cloud Run — ML Inference** | Scale-to-zero enabled, low concurrency per instance, higher memory allocation, invocable only by backend service account | Inference container; scales to zero when idle |
| **Firebase Hosting** | Global CDN, SPA rewrite rules, managed SSL | Edge-cached static frontend; zero-config HTTPS |
| **Cloud Firestore** | Native mode, `asia-southeast1`, composite indexes on key query patterns | NoSQL document store with managed availability and automatic scaling |
| **Cloud Storage** | Regional bucket, lifecycle rules for temp upload cleanup, uniform bucket-level IAM | Image persistence with signed URLs for time-limited access |
| **Firebase Auth** | Email/password provider | Identity management; JWTs validated by backend on every request |
| **Secret Manager** | Stores API keys and service-account credentials | Eliminates hard-coded secrets; accessed via env-var bindings at startup |
| **Cloud Build** | Trigger on `main` push, Docker image build, Cloud Run deployment | Automated CI/CD pipeline for reproducible deployments |

### Cloud Quality → Mechanism Mapping

| Cloud Quality | Deployment Mechanism | GCP Service(s) | Team Owner |
|---------------|---------------------|----------------|------------|
| **Scalability** | Stateless containers with concurrency-based auto-scaling; Firestore auto-sharding | Cloud Run, Firestore | Fang Che Ee |
| **Reliability** | Managed high-availability services, health-check probes, graceful ML fallback, atomic transactions | Cloud Run, Firestore, Cloud Storage | Fang Che Ee |
| **Elasticity** | Scale-to-zero ML containers, CDN-served frontend, configurable min/max instances | Cloud Run, Firebase Hosting | Fang Che Ee (backend), Min Khant Ko (frontend) |
| **Security** | Firebase Auth JWTs, IAM service-to-service auth, Secret Manager, signed URLs, HTTPS termination, rate limiting | Firebase Auth, IAM, Secret Manager, Cloud Storage | Fang Che Ee (backend), Min Khant Ko (frontend auth) |

# 3. Schedule

The compressed 3-week timeline is feasible because the project directly reuses existing Kotlin business logic, an already-configured Firebase project, proven data models, and a curated ML model and nutrition dataset—eliminating typical research and prototyping phases. The **MVP priority** is: authentication, food image upload with ML inference, food matching, meal logging, nutrition calculator, AI coaching, and the analytics dashboard. Social community features are scoped as a secondary deliverable to be integrated once the core pipeline is stable.

| Week | Dates | Milestone | Deliverable |
|------|-------|-----------|-------------|
| 11 | 17–23 Mar | **Infrastructure, ML Service & Core Backend** | GCP project + Firebase Auth/Firestore initialized. Spring Boot backend scaffolded with auth token validation. TFLite model converted and deployed on Cloud Run (TF Serving). Core business logic ported to backend. Image upload endpoint with Cloud Storage. React SPA scaffolded with auth flow and routing. |
| 12 | 24–30 Mar | **Frontend Features & Social Module** | Image upload → classification → confirm → meal logging flow. Dashboard (calorie ring, macro breakdown, AI Coach cards). 7-day analytics charts and food diary. Social API and community feed UI. |
| 13 | 31 Mar–5 Apr | **Integration, Security & Demo** | End-to-end integration testing. Security hardening (rate limiting, input validation, HTTPS enforcement). UI polish and responsive design. Final demo preparation and documentation. **Deadline: 5 April 2026.** |

# 4. Team Members and Roles

| **SN** | **Name** | **Student ID** | **Responsible Components** |
| ------ | -------- | -------------- | -------------------------- |
| 1 | Fang Che Ee | 2301504 | **Cloud ML & Backend Lead** — TF Serving deployment, food classification API, ported backend business logic (`FoodMatchingService`, `NutritionCalculator`, `AICoachRepository`), Cloud Run infrastructure and CI/CD, backend security (token validation, rate limiting). |
| 2 | Min Khant Ko | 2301320 | **Frontend & Social Lead** — React SPA (authentication, image upload, dashboard, analytics, food diary), social feed UI (posts, likes, comments, profiles), Firestore social schema integration, end-to-end testing. |