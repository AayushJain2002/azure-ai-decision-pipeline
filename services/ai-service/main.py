from fastapi import FastAPI, Response, Request
from pydantic import BaseModel
from logging_config import setup_logging
from middleware import add_request_context
from typing import List
from llm import generate_explanation, build_prompt
from prometheus_client import generate_latest, CONTENT_TYPE_LATEST
import logging  

app = FastAPI(title="AI Explanation Service")
setup_logging()
logger = logging.getLogger(__name__)

app.middleware("http")(add_request_context)

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

@app.get("/metrics")
def metrics():
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)

@app.get("/health")
def health():
    return {"status": "ok"}

@app.post("/analyze", response_model=AnalyzeResponse)
def analyze(payload: AnalyzeRequest, request: Request):
    request_id = request.state.request_id
    try:
        data = {
            "decision": payload.decision,
            "riskScore": payload.riskScore,
            "reasons": payload.reasons,
        }
        logger.info("Middleware hit", extra={"request_id": request_id})
        logger.info("Analyze request", extra={
            "request_id": request_id,
            "input": data})

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

        logger.info("Analyze response generated", extra={
            "request_id": request_id,
            "response": response
        })
        return response #gives explanation and suggestions if successful, otherwise fallback options for both 

    except Exception as e:
        logger.error("LLM generation failed", exc_info=True)#only goes up and goes back to zero at restart, tracks number of errors from the endpoint call
        return {
            "explanation": "Error generating explanation.",
            "suggestions": ["Try again later"],
        }
