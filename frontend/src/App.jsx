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

  // label for explanation source
  const getExplanationLabel = (llmStatus) => {
    if (llmStatus === "success") return "LLM-generated explanation";
    return "Rule-based fallback explanation";
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

          {/* explanation type */}
          <div
            style={{
              display: "inline-block",
              padding: "6px 10px",
              border: "1px solid #ccc",
              borderRadius: "999px",
              backgroundColor: "#ffffff",
              marginBottom: "12px",
              fontSize: "14px",
              color: result.llmStatus === "fallback" ? "#666" : "#333",
            }}
          >
            {getExplanationLabel(result.llmStatus)}
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
        <div style={{ color: "red", marginTop: "20px" }}>
          <h3>Error</h3>
          <p>{error.error || "Something went wrong"}</p>
        </div>
      )}
    </div>
  );
}

export default App;