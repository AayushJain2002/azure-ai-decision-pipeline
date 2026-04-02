package com.example.demo.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class Applicant {

    @Min(value = 1, message = "Income must be greater than 0")
    private Integer income;

    @Min(value = 300, message = "Credit score must be at least 300")
    @Max(value = 850, message = "Credit score must be at most 850")
    private Integer creditScore;

    @NotBlank(message = "Employment status is required")
    private String employmentStatus;

    public Integer getIncome() {
        return income;
    }

    public void setIncome(Integer income) {
        this.income = income;
    }

    public Integer getCreditScore() {
        return creditScore;
    }

    public void setCreditScore(Integer creditScore) {
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