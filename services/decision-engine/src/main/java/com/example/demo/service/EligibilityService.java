package com.example.demo.service;

import com.example.demo.model.Applicant;
import com.example.demo.model.DecisionContext;
import com.example.demo.model.EligibilityResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class EligibilityService {

    private static final Logger logger = LoggerFactory.getLogger(EligibilityService.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String fastApiUrl = "http://fastapi:8000/analyze";

    public EligibilityResponse evaluate(Applicant applicant) {
        logger.info("Evaluating applicant: {}", applicant);

        DecisionContext decisionContext = DecisionEngine.evaluate(applicant);
        logger.info("Deterministic decision produced: {}", decisionContext);

        String explanation = buildFallbackExplanation(decisionContext);
        List<String> suggestions = buildFallbackRecommendations(decisionContext);
        String source = "springboot-fallback";
        String llmStatus = "fallback";

        if (!"HARD_STOP".equals(decisionContext.getStatus())) {
            try {
                Map<String, Object> request = objectMapper.convertValue(
                        decisionContext,
                        new TypeReference<Map<String, Object>>() {
                        });

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

                logger.info("Calling FastAPI explanation service at {}", fastApiUrl);
                logger.info("FastAPI request payload: {}", request);

                ResponseEntity<String> responseEntity = restTemplate.exchange(
                        fastApiUrl,
                        HttpMethod.POST,
                        entity,
                        String.class);

                String rawResponse = responseEntity.getBody();
                logger.info("FastAPI response received with status {}", responseEntity.getStatusCode());

                if (rawResponse != null) {
                    Map<String, Object> aiResponse = objectMapper.readValue(rawResponse,
                            new TypeReference<Map<String, Object>>() {
                            });

                    if (aiResponse.get("explanation") != null) {
                        explanation = String.valueOf(aiResponse.get("explanation"));
                    }

                    Object recommendationsObj = aiResponse.get("recommendations");
                    if (recommendationsObj == null) {
                        recommendationsObj = aiResponse.get("suggestions");
                    }
                    if (recommendationsObj instanceof List<?>) {
                        suggestions = new ArrayList<>();
                        for (Object item : (List<?>) recommendationsObj) {
                            suggestions.add(String.valueOf(item));
                        }
                    }

                    if (aiResponse.get("source") != null) {
                        source = String.valueOf(aiResponse.get("source"));
                    }

                    if (aiResponse.get("llmStatus") != null) {
                        llmStatus = String.valueOf(aiResponse.get("llmStatus"));
                    } else if ("llm".equals(source)) {
                        llmStatus = "success";
                    } else {
                        llmStatus = "fallback";
                    }
                }

                if ("fallback".equals(llmStatus)) {
                    logger.warn(
                            "AI explanation service returned fallback output (source={})",
                            source);
                } else {
                    logger.info("AI explanation response processed successfully");
                }

            } catch (Exception e) {
                logger.error(
                        "FastAPI explanation service call failed ({}): {}",
                        e.getClass().getSimpleName(),
                        e.getMessage());
            }
        } else {
            explanation = buildFallbackExplanation(decisionContext);
            suggestions = buildFallbackRecommendations(decisionContext);
            source = "deterministic-hard-stop";
            llmStatus = "fallback";
        }

        EligibilityResponse response = new EligibilityResponse();
        response.setDecisionContext(decisionContext);
        response.setDecision(decisionContext.getDecision());
        response.setRiskScore(decisionContext.getScore() != null ? decisionContext.getScore() : 0.0);
        response.setReasons(decisionContext.getRuleFactors());
        response.setExplanation(explanation);
        response.setRecommendations(suggestions);
        response.setSuggestions(suggestions);
        response.setSource(source);
        response.setLlmStatus(llmStatus);

        logger.info("Final eligibility response: decision={}, llmStatus={}", response.getDecision(), llmStatus);
        return response;
    }

    private String buildFallbackExplanation(DecisionContext decisionContext) {
        String decision = decisionContext.getDecision() != null ? decisionContext.getDecision() : "UNKNOWN";
        List<String> ruleFactors = decisionContext.getRuleFactors();
        List<String> reasonCodes = decisionContext.getReasonCodes();

        String factorSummary;
        if (ruleFactors != null && !ruleFactors.isEmpty()) {
            factorSummary = String.join("; ", ruleFactors);
        } else if (reasonCodes != null && !reasonCodes.isEmpty()) {
            factorSummary = String.join(", ", reasonCodes);
        } else {
            factorSummary = "the applicable deterministic rules";
        }

        return String.format(
                "The system made this decision (%s) based on the following rule factors: %s.",
                decision,
                factorSummary);
    }

    private List<String> buildFallbackRecommendations(DecisionContext decisionContext) {
        List<String> recommendations = new ArrayList<>();
        List<String> missingFields = decisionContext.getMissingFields();
        List<String> reasonCodes = decisionContext.getReasonCodes();

        if (missingFields != null && !missingFields.isEmpty()) {
            recommendations.add("Provide the missing fields: " + String.join(", ", missingFields) + ".");
        }

        if (reasonCodes != null && !reasonCodes.isEmpty()) {
            recommendations.add(
                    "Address the failed criteria flagged as: " + String.join(", ", reasonCodes) + ".");
        }

        recommendations.add("Improve credit profile strength before reapplying.");
        recommendations.add("Increase income stability or reduce debt exposure.");

        return recommendations.subList(0, Math.min(3, recommendations.size()));
    }
}
