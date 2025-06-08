import firebase_admin
from firebase_admin import credentials, firestore, auth # auth is firebase_admin.auth
from fastapi import FastAPI, HTTPException, Depends, status, Header
from fastapi.middleware.cors import CORSMiddleware
from typing import Optional
import os
import subprocess # To execute shell commands
from pydantic import BaseModel
import logging # For logger
import uvicorn

# Initialize Firebase Admin SDK
# Ensure the path to credentials is correct or use environment variables
try:
    # Using a more robust way to get credential path, e.g., from an environment variable
    cred_path = os.getenv("GOOGLE_APPLICATION_CREDENTIALS", "C:/Users/Wehtt/Desktop/GenComms/server/credentials/service-account.json") # Default path if not set
    if not os.path.exists(cred_path):
        logging.warning(f"Firebase credentials not found at {cred_path}. Using default initialization if possible.")
        # Attempt default initialization (e.g. if running in Google Cloud environment)
        firebase_admin.initialize_app(options={
            'databaseURL': os.getenv("FIREBASE_DATABASE_URL", 'https://your-project.firebaseio.com'),
            'storageBucket': os.getenv("FIREBASE_STORAGE_BUCKET", 'your-project.appspot.com')
        })
    else:
        cred = credentials.Certificate(cred_path)
        firebase_admin.initialize_app(cred, {
            'databaseURL': os.getenv("FIREBASE_DATABASE_URL", 'https://your-project.firebaseio.com'),
            'storageBucket': os.getenv("FIREBASE_STORAGE_BUCKET", 'your-project.appspot.com')
        })
    logging.info("Firebase Admin SDK initialized.")
except Exception as e:
    logging.error(f"Failed to initialize Firebase Admin SDK: {e}. Backend services requiring Firebase may not work.")
    # Depending on policy, you might want to exit or continue with limited functionality

db = firestore.client()
# auth is already firebase_admin.auth from the import

app = FastAPI(title="Genesis AI Backend")

# Configure CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=os.getenv("CORS_ALLOWED_ORIGINS", "http://localhost:8081,https://your-domain.com").split(','),
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Basic logger configuration
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Placeholder for user authentication
async def get_current_user(authorization: Optional[str] = Header(None)):
    if not authorization:
        logger.warning("No Authorization header provided. Accessing /toggleLSPosedModule as stub_anon_user.")
        # In a real app, you might raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Not authenticated")
        return {"uid": "stub_anon_user", "roles": ["guest"]}

    try:
        # Example: "Bearer <token_value>"
        scheme, _, token = authorization.partition(' ')
        if not scheme or scheme.lower() != 'bearer' or not token:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid authentication scheme or token missing.")

        # In a real app, verify the token using firebase_auth.verify_id_token(token)
        # decoded_token = auth.verify_id_token(token)
        # logger.info(f"User {decoded_token['uid']} authenticated.")
        # return {"uid": decoded_token["uid"]} # Or more user details

        # For this stub, if a bearer token is present, simulate a valid user
        logger.info(f"Simulating token verification for token starting with: {token[:10]}...")
        return {"uid": "stub_user_from_token", "token_used": token}
    except HTTPException as e: # Re-raise FastAPI's HTTP exceptions
        raise e
    except Exception as e: # Catch other exceptions during token processing
        logger.error(f"Error in get_current_user during token processing: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail=f"Invalid or expired token: {e}")


@app.get("/")
def read_root():
    fb_app_initialized = False
    try:
        firebase_admin.get_app()
        fb_app_initialized = True
    except ValueError: # No app has been initialized with the default name
        pass
    except Exception: # Other unexpected errors
        pass

    return {
        "status": "success",
        "message": "Genesis AI Backend is running!",
        "version": "1.0.0",
        "firebase": {
            "status": "initialized" if fb_app_initialized else "not_initialized",
            "database": "firestore"
        }
    }

@app.get("/health")
def health_check():
    return {"status": "healthy"}

@app.get("/firebase/auth/{token_to_verify}")
async def verify_token_endpoint(token_to_verify: str):
    try:
        decoded_token = auth.verify_id_token(token_to_verify) # Uses the imported firebase_admin.auth
        return {"valid": True, "user_id": decoded_token["uid"]}
    except Exception as e:
        logger.error(f"Token verification failed: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail=str(e))

# NEW: Request/Response Models for LSPosed Module Control
class LSPosedModuleRequest(BaseModel):
    package_name: str
    enable: bool

class LSPosedModuleResponse(BaseModel):
    status: str = "success"
    message: Optional[str] = None
    package_name: str
    enabled: bool

@app.post(
    "/toggleLSPosedModule",
    response_model=LSPosedModuleResponse,
    status_code=status.HTTP_200_OK,
    tags=["LSPosed"]
)
async def toggle_lsposed_module(
    request: LSPosedModuleRequest,
    user: dict = Depends(get_current_user) # Requires user authentication
):
    logger.info(f"User {user.get('uid', 'unknown')} requested to toggle LSPosed module: {request.package_name}, enable: {request.enable}")

    command_success = False
    # output = "" # Not used in this version of the logic
    error_msg = ""
    message_to_client = ""

    try:
        # SIMULATION of root command execution
        logger.warning(f"SIMULATING root command for LSPosed module {request.package_name} enable={request.enable}")

        # Placeholder for actual root command execution.
        # This is where you would use subprocess.run with "su -c your_lsposed_command"
        # Example:
        # cmd = ["su", "-c", f"set_module_status {request.package_name} {'true' if request.enable else 'false'}"]
        # process = subprocess.run(cmd, capture_output=True, text=True, check=False)
        # if process.returncode == 0:
        #     command_success = True
        #     logger.info(f"LSPosed command output: {process.stdout}")
        # else:
        #     command_success = False
        #     error_msg = process.stderr.strip() if process.stderr else "Unknown error from LSPosed command."
        #     logger.error(f"LSPosed command failed for {request.package_name}: {error_msg}")

        # For now, simulate success
        command_success = True # Assume simulation is successful
        simulated_action = "enabled" if request.enable else "disabled"
        message_to_client = f"LSPosed module {request.package_name} {simulated_action} successfully (simulated)."
        logger.info(message_to_client)

    except Exception as e:
        command_success = False
        error_msg = str(e)
        message_to_client = f"Failed to toggle LSPosed module {request.package_name} due to an unexpected server error."
        logger.error(f"Error during LSPosed toggle simulation for {request.package_name}: {error_msg}", exc_info=True)

    if command_success:
        return LSPosedModuleResponse(
            status="success",
            message=message_to_client,
            package_name=request.package_name,
            enabled=request.enable
        )
    else:
        # If command_success is False, it implies a failure.
        # Ensure message_to_client reflects this.
        if not message_to_client or "successfully (simulated)" in message_to_client : # If success message was set before failure
             final_error_detail = error_msg if error_msg else "Simulated command execution failed."
             message_to_client = f"Failed to toggle LSPosed module {request.package_name}. Detail: {final_error_detail}"

        logger.error(f"Responding with 500 error for /toggleLSPosedModule: {message_to_client}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=message_to_client
        )

if __name__ == "__main__":
    # Corrected uvicorn.run call: first argument is a string "module_name:app_instance_name"
    # Assumes this file is named 'app.py' and 'app' is the FastAPI instance.
    uvicorn.run("app:app", host=os.getenv("HOST", "0.0.0.0"), port=int(os.getenv("PORT", 8080)), reload=True)
