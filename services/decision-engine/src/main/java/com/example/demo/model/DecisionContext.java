package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "decision",
        "status",
        "reasonCodes",
        "ruleFactors",
        "score",
        "missingFields",
        "nextStepCategory"
})
public class DecisionContext {

    private String decision;
    private String status;
    private List<String> reasonCodes;
    private List<String> ruleFactors;
    private Double score;
    private List<String> missingFields;
    private String nextStepCategory;

    public DecisionContext() {
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getReasonCodes() {
        return reasonCodes;
    }

    public void setReasonCodes(List<String> reasonCodes) {
        this.reasonCodes = reasonCodes;
    }

    public List<String> getRuleFactors() {
        return ruleFactors;
    }

    public void setRuleFactors(List<String> ruleFactors) {
        this.ruleFactors = ruleFactors;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public List<String> getMissingFields() {
        return missingFields;
    }

    public void setMissingFields(List<String> missingFields) {
        this.missingFields = missingFields;
    }

    public String getNextStepCategory() {
        return nextStepCategory;
    }

    public void setNextStepCategory(String nextStepCategory) {
        this.nextStepCategory = nextStepCategory;
    }

    @Override
    public String toString() {
        return "DecisionContext{" +
                "decision='" + decision + '\'' +
                ", status='" + status + '\'' +
                ", reasonCodes=" + reasonCodes +
                ", ruleFactors=" + ruleFactors +
                ", score=" + score +
                ", missingFields=" + missingFields +
                ", nextStepCategory='" + nextStepCategory + '\'' +
                '}';
    }
}
