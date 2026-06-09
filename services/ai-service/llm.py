import json
import logging
import os

from openai import OpenAI

logger = logging.getLogger(__name__)

FORBIDDEN_RESPONSE_KEYS = {
    "decision",
    "status",
    "reasonCodes",
    "ruleFactors",
    "score",
    "missingFields",
    "nextStepCategory",
    "riskScore",
    "reasons",
    "recommendations",
    "suggestions",
    "explanation",
}


def _llm_force_fallback_enabled() -> bool:
    return os.getenv("LLM_FORCE_FALLBACK", "").lower() in {"1", "true", "yes"}


def get_openai_client():
    api_key = os.getenv("OPENAI_API_KEY")

    if not api_key:
        logger.warning("OPENAI_API_KEY not configured; using rule-based fallback")
        return None

    timeout = float(os.getenv("LLM_TIMEOUT_SECONDS", "30"))
    return OpenAI(api_key=api_key, timeout=timeout)


def fallback_explanation(decision_context: dict) -> str:
    decision = decision_context.get("decision", "UNKNOWN")
    rule_factors = decision_context.get("ruleFactors", [])
    reason_codes = decision_context.get("reasonCodes", [])

    if rule_factors:
        factor_summary = "; ".join(rule_factors)
    elif reason_codes:
        factor_summary = ", ".join(reason_codes)
    else:
        factor_summary = "the applicable deterministic rules"

    return (
        f"The system made this decision ({decision}) based on the following "
        f"rule factors: {factor_summary}."
    )


def fallback_recommendations(decision_context: dict) -> list[str]:
    reason_codes = decision_context.get("reasonCodes", [])
    missing_fields = decision_context.get("missingFields") or []

    recommendations = []

    if missing_fields:
        recommendations.append(
            "Provide the missing fields: "
            + ", ".join(missing_fields)
            + "."
        )

    if reason_codes:
        recommendations.append(
            "Address the failed criteria flagged as: "
            + ", ".join(reason_codes)
            + "."
        )

    recommendations.extend(
        [
            "Improve credit profile strength before reapplying.",
            "Increase income stability or reduce debt exposure.",
        ]
    )

    return recommendations[:3]


def build_explanation_prompt(data: dict) -> str:
    explanation_input = {
        "decision": data.get("decision"),
        "reasonCodes": data.get("reasonCodes", []),
        "ruleFactors": data.get("ruleFactors", []),
    }
    context_json = json.dumps(explanation_input, indent=2)
    formatted_factors = "\n".join(
        f"- {factor}" for factor in explanation_input["ruleFactors"]
    )

    return f"""
You are a financial risk explanation assistant. You do NOT make decisions.

The deterministic decision engine has already produced the final outcome.
Your only job is to explain why that outcome was reached.

AUTHORITATIVE DECISION INPUT (do not change or override):
{context_json}

RULE FACTORS (human-readable):
{formatted_factors}

TASK
Explain clearly why the supplied decision was already made by the rules engine.

OUTPUT
Return STRICT JSON only in this format:
{{
  "explanation": "string"
}}

RULES
- Use only decision, reasonCodes, and ruleFactors from the input.
- You must NOT approve, reject, reclassify, or change the decision.
- Do NOT recommend next steps, actions, or improvements.
- Do NOT output decision, reasonCodes, ruleFactors, or any other fields.
- No markdown
- No extra text outside JSON
- Explanation must reference the supplied ruleFactors and reasonCodes
"""


def build_recommendation_prompt(data: dict) -> str:
    recommendation_input = {
        "decision": data.get("decision"),
        "reasonCodes": data.get("reasonCodes", []),
        "missingFields": data.get("missingFields") or [],
    }
    if data.get("nextStepCategory"):
        recommendation_input["nextStepCategory"] = data["nextStepCategory"]

    context_json = json.dumps(recommendation_input, indent=2)

    return f"""
You are a financial risk guidance assistant. You do NOT make decisions.

The deterministic decision engine has already produced the final outcome.
Your only job is to suggest practical next steps for the applicant.

AUTHORITATIVE DECISION INPUT (do not change or override):
{context_json}

TASK
Provide exactly 3 actionable recommendations based only on:
- the supplied decision
- failed criteria (reasonCodes)
- missing fields (missingFields), when present

OUTPUT
Return STRICT JSON only in this format:
{{
  "recommendations": ["string", "string", "string"]
}}

RULES
- You must NOT approve, reject, reclassify, or change the decision.
- Do NOT reinterpret or explain why the decision was made.
- Do NOT output decision, reasonCodes, missingFields, or any other fields.
- Recommendations must target failed criteria and missing fields only.
- Align with nextStepCategory when provided.
- No markdown
- No extra text outside JSON
"""


def _log_fallback(reason: str, detail: str | None = None) -> None:
    message = f"LLM fallback triggered: {reason}"
    if detail:
        message = f"{message} ({detail})"
    logger.warning(message)


def validate_explanation_response(parsed: dict, decision_context: dict) -> dict:
    if not isinstance(parsed, dict):
        _log_fallback("explanation response is not a JSON object")
        return {"explanation": fallback_explanation(decision_context), "source": "fallback"}

    if any(key in parsed for key in FORBIDDEN_RESPONSE_KEYS if key != "explanation"):
        _log_fallback("explanation response contained forbidden fields")
        return {"explanation": fallback_explanation(decision_context), "source": "fallback"}

    if "explanation" not in parsed or not isinstance(parsed["explanation"], str):
        _log_fallback("explanation field missing or invalid")
        return {"explanation": fallback_explanation(decision_context), "source": "fallback"}

    return {"explanation": parsed["explanation"], "source": "llm"}


def validate_recommendation_response(parsed: dict, decision_context: dict) -> dict:
    if not isinstance(parsed, dict):
        _log_fallback("recommendation response is not a JSON object")
        return {
            "recommendations": fallback_recommendations(decision_context),
            "source": "fallback",
        }

    if "recommendations" in parsed and "suggestions" in parsed:
        _log_fallback("recommendation response contained both recommendations and suggestions")
        return {
            "recommendations": fallback_recommendations(decision_context),
            "source": "fallback",
        }

    recommendations = parsed.get("recommendations", parsed.get("suggestions"))

    extra_keys = set(parsed.keys()) - {"recommendations", "suggestions"}
    if any(key in extra_keys for key in FORBIDDEN_RESPONSE_KEYS):
        _log_fallback("recommendation response contained forbidden fields")
        return {
            "recommendations": fallback_recommendations(decision_context),
            "source": "fallback",
        }

    if not isinstance(recommendations, list):
        _log_fallback("recommendations field missing or not a list")
        return {
            "recommendations": fallback_recommendations(decision_context),
            "source": "fallback",
        }

    if len(recommendations) != 3:
        _log_fallback("recommendations list does not contain exactly 3 items")
        return {
            "recommendations": fallback_recommendations(decision_context),
            "source": "fallback",
        }

    if not all(isinstance(item, str) for item in recommendations):
        _log_fallback("one or more recommendations are not strings")
        return {
            "recommendations": fallback_recommendations(decision_context),
            "source": "fallback",
        }

    return {"recommendations": recommendations, "source": "llm"}


def _call_llm(system_message: str, user_prompt: str, max_tokens: int = 200) -> str | None:
    if _llm_force_fallback_enabled():
        _log_fallback("LLM_FORCE_FALLBACK is enabled")
        return None

    client = get_openai_client()
    if not client:
        return None

    try:
        response = client.chat.completions.create(
            model="gpt-4.1-mini",
            messages=[
                {"role": "system", "content": system_message},
                {"role": "user", "content": user_prompt},
            ],
            max_tokens=max_tokens,
            temperature=0.2,
        )
        return response.choices[0].message.content.strip()
    except Exception as e:
        _log_fallback("OpenAI API call failed", f"{type(e).__name__}: {e}")
        return None


def generate_explanation_text(prompt: str, decision_context: dict) -> dict:
    raw_output = _call_llm(
        "You explain pre-determined financial decisions. "
        "You never change approval, rejection, or classification. "
        "You produce structured, concise, valid JSON with only an explanation field.",
        prompt,
        max_tokens=180,
    )

    if raw_output is None:
        return {"explanation": fallback_explanation(decision_context), "source": "fallback"}

    logger.debug("GPT explanation raw output received")

    try:
        parsed = json.loads(raw_output)
        return validate_explanation_response(parsed, decision_context)
    except json.JSONDecodeError:
        _log_fallback("explanation response was not valid JSON")
        return {"explanation": fallback_explanation(decision_context), "source": "fallback"}


def generate_recommendations(prompt: str, decision_context: dict) -> dict:
    raw_output = _call_llm(
        "You suggest next steps for pre-determined financial decisions. "
        "You never change approval, rejection, or classification. "
        "You produce structured, concise, valid JSON with only a recommendations field.",
        prompt,
        max_tokens=180,
    )

    if raw_output is None:
        return {
            "recommendations": fallback_recommendations(decision_context),
            "source": "fallback",
        }

    logger.debug("GPT recommendation raw output received")

    try:
        parsed = json.loads(raw_output)
        return validate_recommendation_response(parsed, decision_context)
    except json.JSONDecodeError:
        _log_fallback("recommendation response was not valid JSON")
        return {
            "recommendations": fallback_recommendations(decision_context),
            "source": "fallback",
        }


def generate_ai_output(decision_context: dict) -> dict:
    explanation_result = generate_explanation_text(
        build_explanation_prompt(decision_context),
        decision_context,
    )
    recommendation_result = generate_recommendations(
        build_recommendation_prompt(decision_context),
        decision_context,
    )

    sources = {explanation_result["source"], recommendation_result["source"]}
    if sources == {"llm"}:
        source = "llm"
        llm_status = "success"
    elif "llm" in sources:
        source = "partial-fallback"
        llm_status = "fallback"
    else:
        source = "fallback"
        llm_status = "fallback"

    return {
        "decision": decision_context.get("decision", "UNKNOWN"),
        "reasonCodes": decision_context.get("reasonCodes", []),
        "ruleFactors": decision_context.get("ruleFactors", []),
        "explanation": explanation_result["explanation"],
        "recommendations": recommendation_result["recommendations"],
        "source": source,
        "llmStatus": llm_status,
    }
