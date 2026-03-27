package com.example.demo.service;

import com.example.demo.model.Applicant;
import com.example.demo.model.EligibilityResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class EligibilityService {

    private static final Logger logger = LoggerFactory.getLogger(EligibilityService.class);

    public EligibilityResponse evaluate(Applicant applicant) {

        logger.info("Evaluating applicant: {}", applicant);

        // -------------------------------
        // Step 0 - HARD REJECTION RULES
        // -------------------------------
        List<String> reasons = new ArrayList<>();

        if (applicant.getCreditScore() < 600) {
            reasons.add("Credit score is critically low (<600)");
            return buildReject(reasons);
        }

        if (applicant.getEmploymentStatus().equalsIgnoreCase("UNEMPLOYED")
                && applicant.getIncome() < 60000) {
            reasons.add("Unemployed with insufficient income stability");
            return buildReject(reasons);
        }

        if (applicant.getCreditScore() < 670
                && applicant.getEmploymentStatus().equalsIgnoreCase("SELF_EMPLOYED")) {
            reasons.add("Low credit combined with self-employment creates elevated risk");
            return buildReject(reasons);
        }

        // -------------------------------
        // Step 1 - Deterministic Scoring
        // -------------------------------
        double score = 0;

        // CREDIT (50 max)
        if (applicant.getCreditScore() >= 750) {
            score += 50;
        } else if (applicant.getCreditScore() >= 700) {
            score += 40;
            reasons.add("Credit Score is in the moderate range (700-749)");
        } else if (applicant.getCreditScore() >= 650) {
            score += 30;
            reasons.add("Credit Score is below preferred threshold (650-699)");
        } else {
            score += 20;
            reasons.add("Credit Score is low (<650)");
        }

        // INCOME (30 max)
        if (applicant.getIncome() >= 90000) {
            score += 30;
        } else if (applicant.getIncome() >= 75000) {
            score += 27;
        } else if (applicant.getIncome() >= 60000) {
            score += 22;
            reasons.add("Income is below preferred threshold (60000-74999)");
        } else {
            score += 12;
            reasons.add("Income is low (<60000)");
        }

        // EMPLOYMENT (20 max)
        if (applicant.getEmploymentStatus().equalsIgnoreCase("EMPLOYED")) {
            score += 20;
        } else if (applicant.getEmploymentStatus().equalsIgnoreCase("SELF_EMPLOYED")) {
            score += 10;
            reasons.add("Self-employment introduces moderate risk");
        } else {
            score += 0;
            reasons.add("Unemployment is high risk");
        }

        double riskScore = Math.round(score * 100.0) / 100.0;

        // -------------------------------
        // Step 2 - Decision Logic
        // -------------------------------
        String decision;
        if (riskScore >= 85) {
            decision = "APPROVE";
        } else if (riskScore >= 65) {
            decision = "REVIEW";
        } else {
            decision = "REJECT";
        }

        if (reasons.isEmpty()) {
            reasons.add("Strong performance across all evaluation factors");
        }

        // -------------------------------
        // Step 3 - Return deterministic result ONLY
        // -------------------------------
        EligibilityResponse response = new EligibilityResponse();
        response.setDecision(decision);
        response.setRiskScore(riskScore);
        response.setReasons(reasons);

        logger.info("Deterministic response: {}", response);

        return response;
    }

    // -------------------------------
    // Reject Helper (no external calls)
    // -------------------------------
    private EligibilityResponse buildReject(List<String> reasons) {

        EligibilityResponse response = new EligibilityResponse();
        response.setDecision("REJECT");
        response.setRiskScore(50.0);
        response.setReasons(reasons);

        logger.info("Reject response: {}", response);

        return response;
    }
}