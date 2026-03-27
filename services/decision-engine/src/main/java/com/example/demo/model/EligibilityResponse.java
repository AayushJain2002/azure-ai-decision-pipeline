package com.example.demo.model;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "decision", "riskScore", "reasons", "explanation", "suggestions" })
public class EligibilityResponse {

    private String decision;
    private double riskScore;
    private String explanation;
    private List<String> reasons;
    private List<String> suggestions;

    public EligibilityResponse() {
    }

    public EligibilityResponse(String decision, double riskScore, String explanation, List<String> reasons, List<String> suggestions) {
        this.decision = decision;
        this.riskScore = riskScore;
        this.explanation = explanation;
        this.reasons = reasons;
        this.suggestions = suggestions;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public double getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(double riskScore) {
        this.riskScore = riskScore;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public void setReasons(List<String> reasons) {
        this.reasons = reasons;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions;
    }

    @Override
    public String toString() {
        return "EligibilityResponse {" +
                "decision='" + decision + '\'' +
                ", riskScore=" + riskScore +
                ", reasons=" + reasons +
                ", explanation='" + explanation + '\'' +
                ", suggestions=" + suggestions +
                '}';
    }
}