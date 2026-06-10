import { useState } from "react";

function App() {
  // form inputs
  const [form, setForm] = useState({
    income: "",
    creditScore: "",
    employmentStatus: "",
  });

  // API response
  const [result, setResult] = useState(null);

  // error handling
  const [error, setError] = useState(null);

  // loading state
  const [loading, setLoading] = useState(false);

  // update form fields
  const handleChange = (e) => {
    setForm({ ...form, [e.target.name]: e.target.value });
  };

  // call backend API
  const handleSubmit = async () => {
    setResult(null);
    setError(null);

    if (!form.income || !form.creditScore || !form.employmentStatus) {
      setError({ error: "Please fill all fields" });
      return;
    }

    setLoading(true);

    try {
      const response = await fetch("http://localhost:8080/api/evaluate", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          income: Number(form.income),
          creditScore: Number(form.creditScore),
          employmentStatus: form.employmentStatus,
        }),
      });

      let data;
      try {
        data = await response.json();
      } catch {
        setError({ error: "Invalid response from server" });
        return;
      }

      if (!response.ok) {
        setError(data);
        return;
      }

      setResult(data);
    } catch (err) {
      setError({ error: "Failed to connect to backend" });
    } finally {
      setLoading(false);
    }
  };

  // color based on decision
  const getDecisionColor = (decision) => {
    if (decision === "APPROVE") return "green";
    if (decision === "REVIEW") return "orange";
    return "red";
  };

  // badge text tied to backend source (llmStatus as fallback)
  const getExplanationSourceText = (source, llmStatus) => {
    if (source === "llm") {
      return "Explanation Source: LLM";
    }
    if (source) {
      return `Explanation Source: ${source}`;
    }
    if (llmStatus === "success") {
      return "Explanation Source: LLM";
    }
    return "Explanation Source: fallback";
  };

  const isLlmExplanation = (source, llmStatus) => {
    return source === "llm" || (!source && llmStatus === "success");
  };

  const formatFieldLabel = (field) => {
    const labels = {
      income: "Income",
      creditScore: "Credit Score",
      employmentStatus: "Employment Status",
    };
    return labels[field] || field;
  };

  const getErrorMessages = (err) => {
    if (!err || typeof err !== "object") {
      return ["Something went wrong"];
    }

    if (typeof err.error === "string") {
      return [err.error];
    }

    if (typeof err.message === "string") {
      return [err.message];
    }

    const fieldKeys = ["income", "creditScore", "employmentStatus"];
    const fieldMessages = fieldKeys
      .filter((key) => typeof err[key] === "string")
      .map((key) => `${formatFieldLabel(key)}: ${err[key]}`);

    if (fieldMessages.length > 0) {
      return fieldMessages;
    }

    return ["Something went wrong"];
  };

  return (
    <div style={{ padding: "30px", fontFamily: "Arial" }}>
      <h1>AI Decision Pipeline</h1>
      <p>
        Enter applicant information to generate a deterministic decision with an
        LLM-powered explanation.
      </p>

      {/* INPUT SECTION */}
      <div style={{ marginBottom: "20px" }}>
        {/* income input */}
        <div>
          <label>Income</label>
          <br />
          <input
            name="income"
            type="number"
            value={form.income}
            onChange={handleChange}
          />
        </div>

        {/* credit score input */}
        <div style={{ marginTop: "10px" }}>
          <label>Credit Score</label>
          <br />
          <input
            name="creditScore"
            type="number"
            value={form.creditScore}
            onChange={handleChange}
          />
        </div>

        {/* employment dropdown */}
        <div style={{ marginTop: "10px" }}>
          <label>Employment Status</label>
          <br />
          <select
            name="employmentStatus"
            value={form.employmentStatus}
            onChange={handleChange}
          >
            <option value="">Select status</option>
            <option value="EMPLOYED">Employed</option>
            <option value="SELF_EMPLOYED">Self Employed</option>
            <option value="UNEMPLOYED">Unemployed</option>
          </select>
        </div>

        {/* submit button */}
        <button
          onClick={handleSubmit}
          disabled={loading}
          style={{ marginTop: "15px", padding: "8px 16px" }}
        >
          {loading ? "Evaluating..." : "Evaluate"}
        </button>

        {/* loading indicator */}
        {loading && <p>Analyzing credit risk...</p>}
      </div>

      {/* RESULT SECTION */}
      {result && !error && (
        <div
          style={{
            border: "1px solid #ccc",
            borderRadius: "10px",
            padding: "20px",
            marginTop: "20px",
            backgroundColor: "#f9f9f9",
          }}
        >
          {/* decision */}
          <h2 style={{ color: getDecisionColor(result.decision) }}>
            {result.decision}
          </h2>

          {/* score */}
          <p>
            <strong>Risk Score:</strong> {Number(result.riskScore).toFixed(0)}%
          </p>

          {/* explanation source */}
          <div
            style={{
              display: "inline-block",
              padding: "6px 10px",
              border: "1px solid #ccc",
              borderRadius: "999px",
              backgroundColor: "#ffffff",
              marginBottom: "12px",
              fontSize: "14px",
              color: isLlmExplanation(result.source, result.llmStatus)
                ? "#333"
                : "#666",
            }}
          >
            {getExplanationSourceText(result.source, result.llmStatus)}
          </div>

          {/* structured decision context */}
          {result.decisionContext && (
            <div style={{ marginBottom: "12px", fontSize: "14px" }}>
              <p>
                <strong>Status:</strong> {result.decisionContext.status}
              </p>
              {result.decisionContext.nextStepCategory && (
                <p>
                  <strong>Next Step:</strong>{" "}
                  {result.decisionContext.nextStepCategory}
                </p>
              )}
              {result.decisionContext.reasonCodes?.length > 0 && (
                <p>
                  <strong>Reason Codes:</strong>{" "}
                  {result.decisionContext.reasonCodes.join(", ")}
                </p>
              )}
            </div>
          )}

          {/* reasons */}
          <h3>Key Factors</h3>
          <ul style={{ listStyleType: "none", paddingLeft: "0" }}>
            {(result.decisionContext?.ruleFactors || result.reasons)?.map(
              (reason, index) => (
                <li key={index}>{reason}</li>
              )
            )}
          </ul>

          {/* explanation */}
          <h3>Explanation</h3>
          <p>{result.explanation}</p>

          {/* recommendations */}
          <h3>Recommendations</h3>
          <ul style={{ listStyleType: "none", paddingLeft: "0" }}>
            {(result.recommendations || result.suggestions)?.map(
              (recommendation, index) => (
                <li key={index}>{recommendation}</li>
              )
            )}
          </ul>
        </div>
      )}

      {/* ERROR SECTION */}
      {error && (
        <div
          style={{
            color: "#b00020",
            marginTop: "20px",
            border: "1px solid #f5c2c7",
            borderRadius: "8px",
            padding: "16px",
            backgroundColor: "#fff5f5",
          }}
        >
          <h3 style={{ marginTop: 0 }}>Error</h3>
          <ul style={{ margin: 0, paddingLeft: "20px" }}>
            {getErrorMessages(error).map((message, index) => (
              <li key={index}>{message}</li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

export default App;