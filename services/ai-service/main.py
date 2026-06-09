import logging
from typing import List, Optional

from fastapi import FastAPI, Request, Response
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest
from pydantic import BaseModel

from llm import generate_ai_output
from logging_config import setup_logging
from middleware import add_request_context

setup_logging()

logger = logging.getLogger(__name__)

app = FastAPI(title="AI Explanation Service")

app.middleware("http")(add_request_context)


class DecisionContext(BaseModel):
    decision: str
    status: str
    reasonCodes: List[str]
    ruleFactors: List[str]
    score: Optional[float] = None
    missingFields: Optional[List[str]] = None
    nextStepCategory: Optional[str] = None


class AnalyzeResponse(BaseModel):
    decision: str
    reasonCodes: List[str]
    ruleFactors: List[str]
    explanation: str
    recommendations: List[str]
    source: str
    llmStatus: str


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
def analyze(payload: DecisionContext, request: Request):
    request_id = request.state.request_id

    data = payload.model_dump(exclude_none=True)

    logger.info(
        "Analyze request received",
        extra={"request_id": request_id, "input": data},
    )

    result = generate_ai_output(data)

    llm_status = result.get("llmStatus", "fallback")
    if llm_status == "fallback":
        logger.warning(
            "Returning rule-based fallback explanation",
            extra={"request_id": request_id, "source": result.get("source", "unknown")},
        )

    response = {
        "decision": result.get("decision", data.get("decision", "UNKNOWN")),
        "reasonCodes": result.get("reasonCodes", data.get("reasonCodes", [])),
        "ruleFactors": result.get("ruleFactors", data.get("ruleFactors", [])),
        "explanation": result.get(
            "explanation",
            "The system made this decision based on the following rule factors: "
            "the applicable deterministic rules.",
        ),
        "recommendations": result.get(
            "recommendations",
            ["Retry later", "Review the failed criteria", "Contact support if needed"],
        ),
        "source": result.get("source", "unknown"),
        "llmStatus": llm_status,
    }

    logger.info(
        "Analyze response generated",
        extra={"request_id": request_id, "response": response},
    )

    return response
