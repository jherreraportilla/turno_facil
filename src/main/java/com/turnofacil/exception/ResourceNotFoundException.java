package com.turnofacil.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceName, Long id) {
        super(resourceName + " no encontrado con ID: " + id);
    }

    public ResourceNotFoundException(String resourceName, String field, String value) {
        super(resourceName + " no encontrado con " + field + ": " + value);
    }
}
