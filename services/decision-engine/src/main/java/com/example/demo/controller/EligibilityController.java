package com.example.demo.controller;

import com.example.demo.model.Applicant;
import com.example.demo.service.EligibilityService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class EligibilityController {

    private final EligibilityService service;

    public EligibilityController(EligibilityService service) {
        this.service = service;
    }

    @PostMapping("/evaluate")
    public ResponseEntity<?> checkEligibility(@Valid @RequestBody Applicant applicant) {
        return ResponseEntity.ok(service.evaluate(applicant));
    }
}