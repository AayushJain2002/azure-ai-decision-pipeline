package com.example.demo;

import com.example.demo.model.Applicant;
import com.example.demo.model.EmploymentStatus;
import com.example.demo.service.DecisionEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmploymentStatusTest {

    @Test
    void acceptsValidValues() {
        assertEquals(EmploymentStatus.EMPLOYED, EmploymentStatus.from("EMPLOYED"));
        assertEquals(EmploymentStatus.SELF_EMPLOYED, EmploymentStatus.from("self_employed"));
        assertEquals(EmploymentStatus.UNEMPLOYED, EmploymentStatus.from("unemployed"));
    }

    @Test
    void rejectsInvalidValues() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> EmploymentStatus.from("RETIRED"));
        assertTrue(ex.getMessage().contains("Invalid employment status"));
    }

    @Test
    void decisionEngineWorksWithEnum() {
        Applicant applicant = new Applicant();
        applicant.setIncome(80000);
        applicant.setCreditScore(720);
        applicant.setEmploymentStatus(EmploymentStatus.EMPLOYED);

        assertNotNull(DecisionEngine.evaluate(applicant).getDecision());
    }
}

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicantValidationTest {

    @LocalServerPort
    private int port;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private RestClient restClient() {
        return RestClient.create("http://localhost:" + port);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> postEvaluate(String body) {
        try {
            return restClient()
                    .post()
                    .uri("/api/evaluate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(Map.class);
        } catch (HttpClientErrorException.BadRequest ex) {
            try {
                Map<String, Object> responseBody = OBJECT_MAPPER.readValue(
                        ex.getResponseBodyAsString(), Map.class);
                return ResponseEntity.status(ex.getStatusCode()).body(responseBody);
            } catch (Exception parseError) {
                throw ex;
            }
        }
    }

    @Test
    void acceptsValidEmploymentStatusValuesViaApi() {
        for (String status : new String[] {"EMPLOYED", "SELF_EMPLOYED", "UNEMPLOYED"}) {
            ResponseEntity<Map> response = postEvaluate(String.format(
                    "{\"income\":80000,\"creditScore\":720,\"employmentStatus\":\"%s\"}",
                    status));

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody().get("decision"));
        }
    }

    @Test
    void rejectsInvalidEmploymentStatusViaApi() {
        ResponseEntity<Map> response = postEvaluate(
                "{\"income\":80000,\"creditScore\":720,\"employmentStatus\":\"RETIRED\"}");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(String.valueOf(response.getBody().get("employmentStatus"))
                .contains("Invalid employment status"));
    }

    @Test
    void rejectsMissingEmploymentStatusViaApi() {
        ResponseEntity<Map> response = postEvaluate("{\"income\":80000,\"creditScore\":720}");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Employment status is required", response.getBody().get("employmentStatus"));
    }

    @Test
    void rejectsInvalidCreditScoreViaApi() {
        ResponseEntity<Map> response = postEvaluate(
                "{\"income\":80000,\"creditScore\":250,\"employmentStatus\":\"EMPLOYED\"}");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Credit score must be at least 300", response.getBody().get("creditScore"));
    }
}
