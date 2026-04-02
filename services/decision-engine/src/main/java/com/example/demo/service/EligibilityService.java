package com.example.demo.service;

import com.example.demo.model.Applicant;
import com.example.demo.model.EligibilityResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.*;

@Service
public class EligibilityService {

    private static final Logger logger = LoggerFactory.getLogger(EligibilityService.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // private final String fastApiUrl =
    // System.getenv().getOrDefault("AI_SERVICE_URL", "http://fastapi:8000") +
    // "/analyze";
    private final String fastApiUrl = "http://fastapi:8000/analyze";

    public EligibilityResponse evaluate(Applicant applicant) {
        logger.info("=== VERSION 999999 ===");
        logger.info("Evaluating applicant: {}", applicant);

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

        double score = 0;

        if (applicant.getCreditScore() >= 750) {
            score += 50;
        } else if (applicant.getCreditScore() >= 700) {
            score += 40;
            reasons.add("Credit score is in the moderate range (700-749)");
        } else if (applicant.getCreditScore() >= 650) {
            score += 30;
            reasons.add("Credit score is below preferred threshold (650-699)");
        } else {
            score += 20;
            reasons.add("Credit score is low (<650)");
        }

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

        if (applicant.getEmploymentStatus().equalsIgnoreCase("EMPLOYED")) {
            score += 20;
        } else if (applicant.getEmploymentStatus().equalsIgnoreCase("SELF_EMPLOYED")) {
            score += 10;
            reasons.add("Self-employment introduces moderate risk");
        } else {
            reasons.add("Unemployment is high risk");
        }

        double riskScore = Math.round(score * 100.0) / 100.0;

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

        String explanation = "Explanation service unavailable.";
        List<String> suggestions = new ArrayList<>(List.of(
                "Review the decision factors",
                "Improve risk-related inputs where possible",
                "Retry later if explanation service is temporarily unavailable"));

        try {
            Map<String, Object> request = new HashMap<>();
            request.put("decision", decision);
            request.put("riskScore", riskScore);
            request.put("reasons", reasons);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            logger.info("=== NEW VERSION OF SERVICE RUNNING ===");
            logger.info("Calling FastAPI explanation service at {}", fastApiUrl);

            logger.info(" === CALLING FASTAPI ===");
            logger.info("URL: {}", fastApiUrl);
            logger.info("Payload: {}", request);

            ResponseEntity<String> responseEntity = restTemplate.exchange(
                    fastApiUrl,
                    HttpMethod.POST,
                    entity,
                    String.class);

            String rawResponse = responseEntity.getBody();
            logger.info("=== RAW RESPONSE FROM FASTAPI ===");
            logger.info("Raw FastAPI response: {}", rawResponse);

            // Manual parsing
            if (rawResponse != null) {
                Map<String, Object> aiResponse = objectMapper.readValue(rawResponse,
                        new TypeReference<Map<String, Object>>() {
                        });
                logger.info("=== PARSED RESPONSE ===");
                logger.info("Parsed Map: {}", aiResponse);

                if (aiResponse.get("explanation") != null) {
                    explanation = String.valueOf(aiResponse.get("explanation"));
                }

                Object suggestionsObj = aiResponse.get("suggestions");
                if (suggestionsObj instanceof List<?>) {
                    suggestions = new ArrayList<>();
                    for (Object item : (List<?>) suggestionsObj) {
                        suggestions.add(String.valueOf(item));
                    }
                }
            }

            logger.info("AI response received successfully");

        } catch (Exception e) {
            logger.error("=== FASTAPI CALL FAILED ===", e);
        }

        EligibilityResponse response = new EligibilityResponse();
        response.setDecision(decision);
        response.setRiskScore(riskScore);
        response.setReasons(reasons);
        response.setExplanation(explanation);
        response.setSuggestions(suggestions);

        logger.info("Final response: {}", response);
        return response;
    }

    private EligibilityResponse buildReject(List<String> reasons) {
        EligibilityResponse response = new EligibilityResponse();
        response.setDecision("REJECT");
        response.setRiskScore(50.0);
        response.setReasons(reasons);
        response.setExplanation("The application was rejected based on deterministic hard-stop rules.");
        response.setSuggestions(List.of(
                "Improve the highest-risk factor first",
                "Increase income or employment stability",
                "Raise credit score before reapplying"));
        return response;
    }
}