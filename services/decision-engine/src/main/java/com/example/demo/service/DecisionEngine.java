package com.example.demo.service;

import com.example.demo.model.Applicant;
import com.example.demo.model.DecisionContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic decision authority. All approval/review/reject outcomes originate here.
 */
public final class DecisionEngine {

    private DecisionEngine() {
    }

    public static DecisionContext evaluate(Applicant applicant) {
        List<String> reasonCodes = new ArrayList<>();
        List<String> ruleFactors = new ArrayList<>();

        if (applicant.getCreditScore() < 600) {
            reasonCodes.add("CREDIT_CRITICALLY_LOW");
            ruleFactors.add("Credit score is critically low (<600)");
            return buildHardStopReject(reasonCodes, ruleFactors);
        }

        if (applicant.getEmploymentStatus().equalsIgnoreCase("UNEMPLOYED")
                && applicant.getIncome() < 60000) {
            reasonCodes.add("UNEMPLOYED_LOW_INCOME");
            ruleFactors.add("Unemployed with insufficient income stability");
            return buildHardStopReject(reasonCodes, ruleFactors);
        }

        if (applicant.getCreditScore() < 670
                && applicant.getEmploymentStatus().equalsIgnoreCase("SELF_EMPLOYED")) {
            reasonCodes.add("LOW_CREDIT_SELF_EMPLOYED");
            ruleFactors.add("Low credit combined with self-employment creates elevated risk");
            return buildHardStopReject(reasonCodes, ruleFactors);
        }

        double score = 0;

        if (applicant.getCreditScore() >= 750) {
            score += 50;
        } else if (applicant.getCreditScore() >= 700) {
            score += 40;
            reasonCodes.add("CREDIT_MODERATE");
            ruleFactors.add("Credit score is in the moderate range (700-749)");
        } else if (applicant.getCreditScore() >= 650) {
            score += 30;
            reasonCodes.add("CREDIT_BELOW_PREFERRED");
            ruleFactors.add("Credit score is below preferred threshold (650-699)");
        } else {
            score += 20;
            reasonCodes.add("CREDIT_LOW");
            ruleFactors.add("Credit score is low (<650)");
        }

        if (applicant.getIncome() >= 90000) {
            score += 30;
        } else if (applicant.getIncome() >= 75000) {
            score += 27;
        } else if (applicant.getIncome() >= 60000) {
            score += 22;
            reasonCodes.add("INCOME_BELOW_PREFERRED");
            ruleFactors.add("Income is below preferred threshold (60000-74999)");
        } else {
            score += 12;
            reasonCodes.add("INCOME_LOW");
            ruleFactors.add("Income is low (<60000)");
        }

        if (applicant.getEmploymentStatus().equalsIgnoreCase("EMPLOYED")) {
            score += 20;
        } else if (applicant.getEmploymentStatus().equalsIgnoreCase("SELF_EMPLOYED")) {
            score += 10;
            reasonCodes.add("SELF_EMPLOYMENT_RISK");
            ruleFactors.add("Self-employment introduces moderate risk");
        } else {
            reasonCodes.add("UNEMPLOYMENT_RISK");
            ruleFactors.add("Unemployment is high risk");
        }

        double riskScore = Math.round(score * 100.0) / 100.0;

        String decision;
        String nextStepCategory;
        if (riskScore >= 85) {
            decision = "APPROVE";
            nextStepCategory = "PROCEED";
        } else if (riskScore >= 65) {
            decision = "REVIEW";
            nextStepCategory = "MANUAL_REVIEW";
        } else {
            decision = "REJECT";
            nextStepCategory = "IMPROVE_AND_REAPPLY";
        }

        if (ruleFactors.isEmpty()) {
            reasonCodes.add("STRONG_FACTORS");
            ruleFactors.add("Strong performance across all evaluation factors");
        }

        DecisionContext context = new DecisionContext();
        context.setDecision(decision);
        context.setStatus("EVALUATED");
        context.setReasonCodes(reasonCodes);
        context.setRuleFactors(ruleFactors);
        context.setScore(riskScore);
        context.setNextStepCategory(nextStepCategory);
        return context;
    }

    private static DecisionContext buildHardStopReject(
            List<String> reasonCodes,
            List<String> ruleFactors) {
        DecisionContext context = new DecisionContext();
        context.setDecision("REJECT");
        context.setStatus("HARD_STOP");
        context.setReasonCodes(reasonCodes);
        context.setRuleFactors(ruleFactors);
        context.setScore(50.0);
        context.setNextStepCategory("IMPROVE_AND_REAPPLY");
        return context;
    }
}
