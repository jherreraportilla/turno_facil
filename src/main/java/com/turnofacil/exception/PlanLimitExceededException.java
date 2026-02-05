package com.turnofacil.exception;

public class PlanLimitExceededException extends RuntimeException {

    public PlanLimitExceededException(String message) {
        super(message);
    }
}
