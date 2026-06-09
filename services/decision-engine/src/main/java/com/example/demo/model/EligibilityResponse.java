package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

@JsonPropertyOrder({
        "decisionContext",
        "decision",
        "riskScore",
        "reasons",
        "explanation",
        "recommendations",
        "suggestions",
        "source",
        "llmStatus"
})
public class EligibilityResponse {

    private DecisionContext decisionContext;
    private String decision;
    private double riskScore;
    private List<String> reasons;
    private String explanation;
    private List<String> recommendations;
    private List<String> suggestions;
    private String source;
    private String llmStatus;

    public EligibilityResponse() {
    }

    public DecisionContext getDecisionContext() {
        return decisionContext;
    }

    public void setDecisionContext(DecisionContext decisionContext) {
        this.decisionContext = decisionContext;
    }

    public EligibilityResponse(String decision, double riskScore, List<String> reasons, String explanation, List<String> suggestions) {
        this.decision = decision;
        this.riskScore = riskScore;
        this.reasons = reasons;
        this.explanation = explanation;
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

    public List<String> getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(List<String> recommendations) {
        this.recommendations = recommendations;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getLlmStatus() {
        return llmStatus;
    }

    public void setLlmStatus(String llmStatus) {
        this.llmStatus = llmStatus;
    }

    @Override
    public String toString() {
        return "EligibilityResponse{" +
                "decisionContext=" + decisionContext +
                ", decision='" + decision + '\'' +
                ", riskScore=" + riskScore +
                ", reasons=" + reasons +
                ", explanation='" + explanation + '\'' +
                ", recommendations=" + recommendations +
                ", suggestions=" + suggestions +
                ", source='" + source + '\'' +
                ", llmStatus='" + llmStatus + '\'' +
                '}';
    }
}