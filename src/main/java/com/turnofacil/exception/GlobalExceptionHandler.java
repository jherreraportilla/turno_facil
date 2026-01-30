package com.turnofacil.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Value("${app.show-error-details:false}")
    private boolean showErrorDetails;

    // ==================== API ERRORS (JSON) ====================

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {

        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI()));
        }
        // Para peticiones web, devolver página de error
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(com.turnofacil.exception.AccessDeniedException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            com.turnofacil.exception.AccessDeniedException ex, HttpServletRequest request) {

        log.warn("Acceso denegado: {} - IP: {}", ex.getMessage(), getClientIp(request));
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(buildErrorResponse(HttpStatus.FORBIDDEN, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(BusinessValidationException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleBusinessValidation(
            BusinessValidationException ex, HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleRateLimit(
            RateLimitExceededException ex, HttpServletRequest request) {

        log.warn("Rate limit excedido - IP: {} - Path: {}", getClientIp(request), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(buildErrorResponse(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleIllegalState(
            IllegalStateException ex, HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );

        Map<String, Object> response = buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Error de validacion",
                request.getRequestURI()
        );
        response.put("fieldErrors", fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleSpringAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {

        log.warn("Spring Security - Acceso denegado: {} - IP: {}", request.getRequestURI(), getClientIp(request));
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(buildErrorResponse(HttpStatus.FORBIDDEN, "Acceso denegado", request.getRequestURI()));
    }

    // ==================== GENERIC ERROR HANDLER ====================

    @ExceptionHandler(Exception.class)
    public Object handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Error no controlado en {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        String message = showErrorDetails
                ? ex.getMessage()
                : "Ha ocurrido un error interno. Por favor, contacta con soporte.";

        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, message, request.getRequestURI()));
        }

        // Para peticiones web, redirigir a página de error
        ModelAndView mav = new ModelAndView("error/500");
        mav.addObject("message", message);
        mav.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        return mav;
    }

    // ==================== HELPERS ====================

    private Map<String, Object> buildErrorResponse(HttpStatus status, String message, String path) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("status", status.value());
        response.put("error", status.getReasonPhrase());
        response.put("message", message);
        response.put("path", path);
        return response;
    }

    private boolean isApiRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        String accept = request.getHeader("Accept");
        return path.startsWith("/api/") ||
               (accept != null && accept.contains("application/json"));
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
