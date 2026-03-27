from fastapi import FastAPI, Request
from pydantic import BaseModel
from slowapi import Limiter
from slowapi.util import get_remote_address
from slowapi.errors import RateLimitExceeded
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware

import requests, os
from llm import generate_explanation, build_prompt

# ---------------------------
# FastAPI Init
# ---------------------------
app = FastAPI()
print("FastAPI orchestrator started")


# ---------------------------
# CORS
# ---------------------------
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # tighten later
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ---------------------------
# Rate Limiting
# ---------------------------
limiter = Limiter(key_func=get_remote_address)
app.state.limiter = limiter


@app.exception_handler(RateLimitExceeded)
def rate_limit_handler(request: Request, exc: RateLimitExceeded):
    return JSONResponse(
        status_code=429,
        content={"error": "Rate limit exceeded. Try again later."},
    )


# ---------------------------
# Request Schema (ONLY INPUT DATA)
# ---------------------------
class AnalyzeRequest(BaseModel):
    income: int
    credit_score: int
    employment_status: str


# ---------------------------
# Root
# ---------------------------
@app.get("/")
def root():
    return {"message": "AI Decision Orchestrator Running"}


# ---------------------------
# Analyze Endpoint
# ---------------------------
@app.post("/analyze")
@limiter.limit("5/minute")
async def analyze(request: Request, payload: AnalyzeRequest):

    input_data = payload.model_dump()
    print("Incoming data:", input_data)

    DECISION_ENGINE_URL = os.getenv(
        "DECISION_ENGINE_URL", "http://localhost:8080"  # failback for local dev
    )

    # ---------------------------
    # 1. Call Spring Boot (deterministic engine)
    # ---------------------------
    try:
        decision_response = requests.post(
            f"{DECISION_ENGINE_URL}/api/evaluate",
            json={
                "income": input_data["income"],
                "creditScore": input_data["credit_score"],
                "employmentStatus": input_data["employment_status"],
            },
            timeout=3,
        )
        decision_response.raise_for_status()
        decision_data = decision_response.json()
        # print("Spring Boot RAW response:", decision_response.text)
        # print("Spring Boot JSON parsed:", decision_data)

    except requests.exceptions.ConnectionError:
        return {
        "error": "Decision engine unreachable (service down)"
        }

    except requests.exceptions.RequestException as e:
        return {
        "error": "Decision engine error",
        "details": str(e)
        }

    #Validate Response
    if not isinstance(decision_data, dict):
        return {"error": "Invalid response from decision engine"}

    required_fields = ["decision", "riskScore", "reasons"]

    for field in required_fields:
        if field not in decision_data:
            return {
                "error": "Malformed response from decision engine",
                "missing_field": field,
            }

    # ---------------------------
    # 2. Combine input + decision output
    # ---------------------------
    combined_data = {
        "income": input_data["income"],
        "credit_score": input_data["credit_score"],
        "employment_status": input_data["employment_status"],
        "decision": decision_data.get("decision", "UNKNOWN"),
        "riskScore": decision_data.get("riskScore", 0),
        "reasons": decision_data.get("reasons", ["No reasons provided"]),
    }

    # ---------------------------
    # 3. Build prompt (LLM layer)
    # ---------------------------
    prompt = build_prompt(combined_data)

    # ---------------------------
    # 4. Generate explanation
    # ---------------------------
    gpt_output = generate_explanation(prompt)

    # ---------------------------
    # 5. Return final response
    # ---------------------------
    response = {
        "decision": decision_data.get("decision"),
        "riskScore": decision_data.get("riskScore"),
        "reasons": decision_data.get("reasons"),
        "explanation": gpt_output.get("explanation"),
        "suggestions": gpt_output.get("suggestions"),
    }

    print("Response:", response)
    return response
