# GCP Production Deployment Guide

This document outlines how to deploy NutriScan Cloud to Google Cloud Platform securely using Secret Manager, fulfilling the architectural requirements outlined in the proposal.

## 1. Secret Manager Setup
Rather than checking in `.env` or `firebase-service-account.json` files, we use GCP Secret Manager to securely inject credentials into Cloud Run containers at runtime.

### Create Secrets in GCP
Execute these commands using the `gcloud` CLI:

```bash
# 1. Store the Firebase Service Account JSON
gcloud secrets create FIREBASE_SERVICE_ACCOUNT --replication-policy="automatic"
gcloud secrets versions add FIREBASE_SERVICE_ACCOUNT --data-file="firebase-service-account.json"

# 2. Grant the Cloud Run default service account permission to access the secret
gcloud secrets add-iam-policy-binding FIREBASE_SERVICE_ACCOUNT \
    --member="serviceAccount:YOUR_PROJECT_NUMBER-compute@developer.gserviceaccount.com" \
    --role="roles/secretmanager.secretAccessor"
```

## 2. Deploying with Secrets
When deploying the Backend API to Cloud Run, we map the Secret Manager value directly to an environment variable or a volume mount. Our backend expects `FIREBASE_SERVICE_ACCOUNT_PATH`.

```bash
# Deploying the backend and mounting the secret as a file
gcloud run deploy nutriscan-backend \
  --image gcr.io/YOUR_PROJECT_ID/nutriscan-backend \
  --region asia-southeast1 \
  --platform managed \
  --allow-unauthenticated \
  --set-env-vars="FIREBASE_PROJECT_ID=nutriscan-2c485" \
  --set-env-vars="FIREBASE_STORAGE_BUCKET=nutriscan-2c485.appspot.com" \
  --set-env-vars="INFERENCE_SERVICE_URL=https://nutriscan-inference-xxx-as.a.run.app" \
  --update-secrets="/app/firebase-service-account.json=FIREBASE_SERVICE_ACCOUNT:latest" \
  --set-env-vars="FIREBASE_SERVICE_ACCOUNT_PATH=/app/firebase-service-account.json"
```

> **Note:** The `--update-secrets` flag safely mounts the GCP secret directly into the container's filesystem at runtime. The `.dockerignore` file ensures your local `firebase-service-account.json` is NEVER copied into the image.

## 3. Deploying the Inference Service (Internal Only)
The ML microservice should not be publicly accessible. It is deployed as an internal service:

```bash
gcloud run deploy nutriscan-inference \
  --image gcr.io/YOUR_PROJECT_ID/nutriscan-inference \
  --region asia-southeast1 \
  --platform managed \
  --no-allow-unauthenticated
```
*You must grant the backend's service account the `roles/run.invoker` role on the `nutriscan-inference` service to allow the backend to call the ML service securely.*

## 4. CI/CD Note
The `cloudbuild.yaml` at the root of the repository automates building the Docker images, pushing them to Artifact Registry, and triggering the `gcloud run deploy` commands above. You must configure Cloud Build triggers in the GCP Console to automatically run this on pushes to the `main` branch.
