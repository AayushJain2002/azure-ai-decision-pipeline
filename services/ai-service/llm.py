import os
import json
from openai import OpenAI


# ---------------------------
# OpenAI Client
# ---------------------------
def get_openai_client():
    api_key = os.getenv("OPENAI_API_KEY")
    if not api_key:
        return None
    return OpenAI(api_key=api_key)


# ---------------------------
# Fallback Response
# ---------------------------
def fallback_response():
    return {
        "explanation": "LLM unavailable. Using fallback response.",
        "suggestions": [
            "Improve credit score",
            "Increase income stability",
            "Maintain consistent employment"
        ],
        "source": "fallback"
    }


# ---------------------------
# Prompt Builder (moved here)
# ---------------------------
def build_prompt(data: dict) -> str:
    return f"""
You are a financial risk analyst.

Given:
- Income: {data['income']}
- Credit Score: {data['credit_score']}
- Employment Status: {data['employment_status']}
- Risk Decision: {data['decision']}
- Risk Score: {data['riskScore']}
- Key Risk Factors: {data['reasons']}

Return STRICT JSON:
{{
    "explanation": "...",
    "suggestions": ["...", "...", "..."]
}}

Rules:
- No extra text
- No hallucination
- Only use given inputs
"""


# ---------------------------
# LLM Execution
# ---------------------------
def generate_explanation(prompt: str):
    client = get_openai_client()

    if not client:
        return fallback_response()

    try:
        response = client.chat.completions.create(
            model="gpt-4.1-mini",
            messages=[
                {"role": "system", "content": "You are a precise financial assistant."},
                {"role": "user", "content": prompt},
            ],
            max_tokens=200,
            temperature=0.2,
        )

        raw_output = response.choices[0].message.content
        print("GPT RAW OUTPUT:", raw_output)

        try:
            parsed = json.loads(raw_output)
            parsed["source"] = "llm"
            return parsed
        except:
            return fallback_response()

    except Exception as e:
        print("OpenAI error:", e)
        return fallback_response()