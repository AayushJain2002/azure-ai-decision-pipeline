package com.example.demo.model;

import jakarta.validation.constraints.*;

public class Applicant {
    
    // set limits on income, creditScore, and employment status
    @Min(value = 1, message = "Income must be greater than 0")
    private Integer income;

    @Min(value = 300, message = "Credit score must be at least 300")
    @Max(value = 850, message = "Credit score must be at most 850")
    private Integer creditScore;

    @NotBlank(message = "Employment status is required")
    private String employmentStatus;

    // getters and setters
    public Integer getIncome() {
        return income;
    }

    public void setIncome(int income) {
        this.income = income;
    }

    public Integer getCreditScore() {
        return creditScore;
    }

    public void setCreditScore(int creditScore) {
        this.creditScore = creditScore;
    }

    public String getEmploymentStatus() {
        return employmentStatus;
    }

    public void setEmploymentStatus(String employmentStatus) {
        this.employmentStatus = employmentStatus;
    }

    @Override
    public String toString() {
        return "Applicant{" +
                "income=" + income +
                ", creditScore=" + creditScore +
                ", employmentStatus='" + employmentStatus + '\'' +
                '}';
    }
}
