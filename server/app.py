import firebase_admin
from firebase_admin import credentials, firestore, auth
from fastapi import FastAPI, HTTPException, Depends
from fastapi.middleware.cors import CORSMiddleware
from typing import Optional
import os

# Initialize Firebase Admin SDK
cred = credentials.Certificate("C:/Users/Wehtt/Desktop/GenComms/server/credentials/service-account.json")

# Initialize Firebase Admin SDK with existing catalog
firebase_admin.initialize_app(cred, {
    'databaseURL': 'https://your-project.firebaseio.com',
    'storageBucket': 'your-project.appspot.com'
})

db = firestore.client()

# Initialize Firebase Auth
auth = firebase_admin.auth

# Initialize Firebase Storage
storage = firebase_admin.storage

# Initialize Firebase Analytics
analytics = firebase_admin.analytics
firebase_admin.initialize_app(cred)

db = firestore.client()

app = FastAPI(title="Genesis AI Backend")

# Configure CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:8081", "https://your-domain.com"],  # Update with your actual frontend URLs
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/")
def read_root():
    return {
        "status": "success",
        "message": "Genesis AI Backend is running!",
        "version": "1.0.0",
        "firebase": {
            "status": "initialized",
            "database": "firestore"
        }
    }

@app.get("/health")
def health_check():
    return {"status": "healthy"}

@app.get("/firebase/auth/{token}")
async def verify_token(token: str):
    try:
        decoded_token = auth.verify_id_token(token)
        return {"valid": True, "user_id": decoded_token["uid"]}
    except Exception as e:
        raise HTTPException(status_code=401, detail=str(e))

if __name__ == "__main__":
    uvicorn.run("app:app", host="0.0.0.0", port=8080, reload=True)
