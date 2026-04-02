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

  return (
    <div style={{ padding: "30px", fontFamily: "Arial" }}>
      <h1>Check Your Risk</h1>

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
            <option value="employed">Employed</option>
            <option value="self-employed">Self Employed</option>
            <option value="unemployed">Unemployed</option>
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
            <strong>Risk Score:</strong> {result.riskScore.toFixed(0)}%
          </p>

          {/* reasons */}
          <h3>Key Factors</h3>
          <ul style={{ listStyleType: "none", paddingLeft: "0" }}>
            {result.reasons?.map((r, i) => (
              <li key={i}>{r}</li>
            ))}
          </ul>

          {/* explanation */}
          <h3>Explanation</h3>
          <p>{result.explanation}</p>

          {/* suggestions */}
          <h3>Suggestions</h3>
          <ul style={{ listStyleType: "none", paddingLeft: "0" }}>
            {result.suggestions?.map((s, i) => (
              <li key={i}>{s}</li>
            ))}
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