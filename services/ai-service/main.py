from fastapi import FastAPI
from pydantic import BaseModel
from typing import List
from llm import generate_explanation, build_prompt

app = FastAPI(title="AI Explanation Service")


class AnalyzeRequest(BaseModel):
    decision: str
    riskScore: float
    reasons: List[str]


class AnalyzeResponse(BaseModel):
    explanation: str
    suggestions: List[str]


@app.get("/")
def root():
    return {"message": "AI Service Running"}


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/analyze", response_model=AnalyzeResponse)
def analyze(request: AnalyzeRequest):
    try:
        data = {
            "decision": request.decision,
            "riskScore": request.riskScore,
            "reasons": request.reasons,
        }

        print("FASTAPI /analyze request:", data)

        prompt = build_prompt(data)
        result = generate_explanation(prompt)

        response = {
            "explanation": result.get(
                "explanation", "Unable to generate explanation at this time."
            ),
            "suggestions": result.get(
                "suggestions", ["Retry later", "Review the risk factors provided"]
            ),
        }

        print("FASTAPI /analyze response:", response)
        return response

    except Exception as e:
        print("FastAPI error:", e)
        return {
            "explanation": "Error generating explanation.",
            "suggestions": ["Try again later"],
        }
