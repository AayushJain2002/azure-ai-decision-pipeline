import logging
from typing import List

from fastapi import FastAPI, Request, Response
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest
from pydantic import BaseModel

from llm import build_prompt, generate_explanation
from logging_config import setup_logging
from middleware import add_request_context

setup_logging()

logger = logging.getLogger(__name__)

app = FastAPI(title="AI Explanation Service")

app.middleware("http")(add_request_context)


class AnalyzeRequest(BaseModel):
    decision: str
    riskScore: float
    reasons: List[str]


class AnalyzeResponse(BaseModel):
    explanation: str
    suggestions: List[str]
    source: str


@app.get("/")
def root():
    return {"message": "AI Service Running"}


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/metrics")
def metrics():
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)


@app.post("/analyze", response_model=AnalyzeResponse)
def analyze(payload: AnalyzeRequest, request: Request):
    request_id = request.state.request_id

    data = {
        "decision": payload.decision,
        "riskScore": payload.riskScore,
        "reasons": payload.reasons,
    }

    logger.info(
        "Analyze request received",
        extra={"request_id": request_id, "input": data},
    )

    prompt = build_prompt(data)
    result = generate_explanation(prompt)

    response = {
        "explanation": result.get(
            "explanation",
            "Unable to generate explanation at this time.",
        ),
        "suggestions": result.get(
            "suggestions",
            ["Retry later", "Review the risk factors provided"],
        ),
        "source": result.get("source", "unknown"),
    }

    logger.info(
        "Analyze response generated",
        extra={"request_id": request_id, "response": response},
    )

    return response