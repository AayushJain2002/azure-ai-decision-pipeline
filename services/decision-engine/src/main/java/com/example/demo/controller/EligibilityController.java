package com.example.demo.controller;

import com.example.demo.model.Applicant;
import com.example.demo.service.EligibilityService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
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