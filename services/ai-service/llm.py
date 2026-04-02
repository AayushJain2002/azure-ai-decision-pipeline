import os
import json
from openai import OpenAI


def get_openai_client():
    api_key = os.getenv("OPENAI_API_KEY")
    if not api_key:
        print("OPENAI_API_KEY not found. Using fallback response.")
        return None
    return OpenAI(api_key=api_key)


def fallback_response():
    return {
        "explanation": (
            "The applicant was evaluated using deterministic risk rules. "
            "The explanation service is currently unavailable, so a fallback response was used."
        ),
        "suggestions": [
            "Improve credit profile strength",
            "Increase income stability",
            "Address the listed risk factors before reapplying"
        ],
        "source": "fallback"
    }


def build_prompt(data: dict) -> str:
    formatted_reasons = "\n".join(f"- {reason}" for reason in data["reasons"])

    return f"""
You are a precise financial risk explanation assistant.

INPUT
Decision: {data["decision"]}
Risk Score: {data["riskScore"]}
Risk Factors:
{formatted_reasons}

TASK
1. Explain clearly why this credit decision was made.
2. Provide exactly 3 actionable suggestions that are practical for the applicant.

OUTPUT
Return STRICT JSON only in this format:
{{
  "explanation": "string",
  "suggestions": ["string", "string", "string"]
}}

RULES
- No markdown
- No extra text outside JSON
- Explanation must directly reference the supplied risk factors
- Suggestions must be specific, practical, and applicant-focused
"""


def generate_explanation(prompt: str):
    client = get_openai_client()

    if not client:
        return fallback_response()

    try:
        response = client.chat.completions.create(
            model="gpt-4.1-mini",
            messages=[
                {
                    "role": "system",
                    "content": "You produce structured, concise, valid JSON for financial risk explanations."
                },
                {
                    "role": "user",
                    "content": prompt
                }
            ],
            max_tokens=250,
            temperature=0.2,
        )

        raw_output = response.choices[0].message.content.strip()
        print("GPT RAW OUTPUT:", raw_output)

        try:
            parsed = json.loads(raw_output)

            if not isinstance(parsed, dict):
                print("Parsed LLM output is not an object. Using fallback.")
                return fallback_response()

            if "explanation" not in parsed or "suggestions" not in parsed:
                print("Missing required keys. Using fallback.")
                return fallback_response()

            if not isinstance(parsed["suggestions"], list):
                print("Suggestions is not a list. Using fallback.")
                return fallback_response()

            parsed["source"] = "llm"
            return parsed

        except json.JSONDecodeError:
            print("Failed to parse LLM JSON. Using fallback.")
            return fallback_response()

    except Exception as e:
        print("OpenAI error:", e)
        return fallback_response()