package com.turnofacil.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class AccessDeniedException extends RuntimeException {

    public AccessDeniedException(String message) {
        super(message);
    }

    public AccessDeniedException(String resourceName, Long resourceId) {
        super("No tienes permiso para acceder a " + resourceName + " con ID: " + resourceId);
    }
}
