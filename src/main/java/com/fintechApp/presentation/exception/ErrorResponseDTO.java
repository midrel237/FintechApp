package com.fintechApp.presentation.exception;

import java.time.Instant;
import java.util.List;

import lombok.Data;

/**
 * Reproduit le format d'erreur unique défini au contrat d'API
 * (Partie 2 — "Format d'erreur unique") :
 * { "error": { code, message, status, timestamp, path, details } }
 */
@Data
public class ErrorResponseDTO {
    private final ErrorBody error;

    public ErrorResponseDTO(String code, String message, int status, String path, List<ErrorDetail> details) {
        this.error = new ErrorBody(code, message, status, Instant.now().toString(), path, details);
    }

    @Data
    public static class ErrorBody {
        private final String code;
        private final String message;
        private final int status;
        private final String timestamp;
        private final String path;
        private final List<ErrorDetail> details;
    }

    @Data
    public static class ErrorDetail {
        private final String field;
        private final String reason;
    }
}
