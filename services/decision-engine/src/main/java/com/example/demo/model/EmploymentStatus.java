package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum EmploymentStatus {
    EMPLOYED,
    UNEMPLOYED,
    SELF_EMPLOYED;

    @JsonCreator
    public static EmploymentStatus from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Employment status cannot be null");
        }

        try {
            return EmploymentStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid employment status. Allowed values: EMPLOYED, UNEMPLOYED, SELF_EMPLOYED"
            );
        }
    }
}